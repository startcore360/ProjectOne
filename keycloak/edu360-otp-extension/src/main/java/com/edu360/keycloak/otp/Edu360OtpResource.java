package com.edu360.keycloak.otp;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.utils.HmacOTP;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.utils.CredentialHelper;
import org.keycloak.utils.TotpUtils;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * /realms/{realm}/edu360-otp/... - in-app authenticator enrolment for EDU 360.
 *
 * WHO MAY CALL: only a bearer token from the MASTER realm whose user holds master's "admin" role,
 * or "manage-users" on this realm's management client - i.e. the EDU 360 API's existing Keycloak
 * admin token (KeycloakAdminClient). A browser never calls this directly; the API first proves
 * the user's password, then calls here.
 *
 * WHERE THE SECRET LIVES: the new TOTP secret is generated here and parked for 10 minutes in
 * Keycloak's single-use object store (the same store Keycloak uses for its own action tokens),
 * keyed by a random enrolment id. The secret is returned once so the user can scan it (QR) or
 * type it; the EDU 360 API never stores it. On a correct code the credential is saved on the
 * user exactly as Keycloak's own "Configure OTP" page would, and CONFIGURE_TOTP is cleared.
 *
 * LIMITS: 5 wrong codes per enrolment, then the enrolment is discarded and must be restarted.
 * Starting an enrolment is refused (409) when the user already has an authenticator, so this
 * can never be used to add a second device silently.
 */
public class Edu360OtpResource {

    private static final Logger LOG = Logger.getLogger(Edu360OtpResource.class);

    static final int LIFESPAN_SECONDS = 600;
    static final int MAX_ATTEMPTS = 5;
    private static final String KEY_PREFIX = "edu360-otp-enrolment:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeycloakSession session;

    public Edu360OtpResource(KeycloakSession session) {
        this.session = session;
    }

    /** Body: {"username": "..."}. Answers {enrolmentId, qrCode (base64 PNG), secret, expiresInSeconds}. */
    @POST
    @Path("enrolment")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(Map<String, Object> body) {
        Response denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        RealmModel realm = session.getContext().getRealm();
        UserModel user = findUser(realm, body);
        if (user == null) {
            return error(Response.Status.NOT_FOUND, "user_not_found");
        }
        if (hasOtp(user)) {
            return error(Response.Status.CONFLICT, "already_enrolled");
        }

        String secret = HmacOTP.generateSecret(20);
        String enrolmentId = randomId();
        Map<String, String> notes = new HashMap<>();
        notes.put("realmId", realm.getId());
        notes.put("userId", user.getId());
        notes.put("secret", secret);
        notes.put("attempts", "0");
        store().put(KEY_PREFIX + enrolmentId, LIFESPAN_SECONDS, notes);

        Map<String, Object> out = new HashMap<>();
        out.put("enrolmentId", enrolmentId);
        out.put("qrCode", TotpUtils.qrCode(secret, realm, user));
        out.put("secret", TotpUtils.encode(secret));
        out.put("expiresInSeconds", LIFESPAN_SECONDS);
        return Response.ok(out).build();
    }

    /** Body: {"username": "...", "code": "123456", "deviceName": "optional label"}. 204 on success. */
    @POST
    @Path("enrolment/{enrolmentId}/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirm(@PathParam("enrolmentId") String enrolmentId, Map<String, Object> body) {
        Response denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        RealmModel realm = session.getContext().getRealm();
        String key = KEY_PREFIX + enrolmentId;
        Map<String, String> notes = store().get(key);
        UserModel user = findUser(realm, body);
        // Same 404 for "expired", "unknown id" and "id belongs to someone else": reveals nothing.
        if (notes == null || user == null
                || !realm.getId().equals(notes.get("realmId"))
                || !user.getId().equals(notes.get("userId"))) {
            return error(Response.Status.NOT_FOUND, "enrolment_not_found");
        }
        if (hasOtp(user)) {
            store().remove(key);
            return error(Response.Status.CONFLICT, "already_enrolled");
        }

        OTPPolicy policy = realm.getOTPPolicy();
        String code = body == null || body.get("code") == null ? null : body.get("code").toString();
        String secret = notes.get("secret");
        if (!TotpCheck.isValid(code, secret, policy)) {
            int attempts = Integer.parseInt(notes.getOrDefault("attempts", "0")) + 1;
            if (attempts >= MAX_ATTEMPTS) {
                store().remove(key);
                return error(Response.Status.BAD_REQUEST, "too_many_attempts");
            }
            // The store hands back a read-only map: write a copy with the new attempt count.
            Map<String, String> updated = new HashMap<>(notes);
            updated.put("attempts", Integer.toString(attempts));
            store().replace(key, updated);
            Map<String, Object> out = new HashMap<>();
            out.put("error", "invalid_code");
            out.put("attemptsLeft", MAX_ATTEMPTS - attempts);
            return Response.status(Response.Status.BAD_REQUEST).entity(out).build();
        }

        String label = deviceName(body);
        OTPCredentialModel credential = OTPCredentialModel.createFromPolicy(realm, secret, label);
        if (!CredentialHelper.createOTPCredential(session, realm, user, code.trim(), credential)) {
            return error(Response.Status.BAD_REQUEST, "invalid_code");
        }
        user.removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
        store().remove(key);
        LOG.infof("EDU 360: authenticator enrolled for user %s in realm %s", user.getId(), realm.getName());
        return Response.noContent().build();
    }

    // --------------------------------------------------------------------------------- helpers

    /** null = allowed; otherwise the response to send (401 / 403). */
    private Response requireAdmin() {
        RealmModel target = session.getContext().getRealm();
        RealmModel master = session.realms().getRealmByName("master");
        if (master == null) {
            return error(Response.Status.FORBIDDEN, "forbidden");
        }
        AuthenticationManager.AuthResult auth;
        try {
            session.getContext().setRealm(master);
            auth = new AppAuthManager.BearerTokenAuthenticator(session)
                    .setRealm(master)
                    .setUriInfo(session.getContext().getUri())
                    .setConnection(session.getContext().getConnection())
                    .setHeaders(session.getContext().getRequestHeaders())
                    .authenticate();
        } finally {
            session.getContext().setRealm(target);
        }
        if (auth == null || auth.getUser() == null) {
            return error(Response.Status.UNAUTHORIZED, "unauthorized");
        }
        // Master "admin" (the dev bootstrap admin), or - for a production service-account client -
        // the narrower "manage-users" role on this realm's management client in master.
        RoleModel admin = master.getRole("admin");
        if (admin != null && auth.getUser().hasRole(admin)) {
            return null;
        }
        ClientModel realmManagement = master.getClientByClientId(target.getName() + "-realm");
        RoleModel manageUsers = realmManagement == null ? null : realmManagement.getRole("manage-users");
        if (manageUsers != null && auth.getUser().hasRole(manageUsers)) {
            return null;
        }
        return error(Response.Status.FORBIDDEN, "forbidden");
    }

    private UserModel findUser(RealmModel realm, Map<String, Object> body) {
        Object username = body == null ? null : body.get("username");
        if (username == null || username.toString().isBlank()) {
            return null;
        }
        return session.users().getUserByUsername(realm, username.toString().trim());
    }

    private static boolean hasOtp(UserModel user) {
        return user.credentialManager().getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE).findAny().isPresent();
    }

    private static String deviceName(Map<String, Object> body) {
        Object name = body == null ? null : body.get("deviceName");
        String value = name == null ? "" : name.toString().trim();
        if (value.isEmpty()) {
            return "Authenticator app";
        }
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private SingleUseObjectProvider store() {
        return session.singleUseObjects();
    }

    private static String randomId() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Response error(Response.Status status, String code) {
        return Response.status(status).entity(Map.of("error", code)).type(MediaType.APPLICATION_JSON).build();
    }
}
