package com.edu360.api.platformops.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.edu360.api.config.PlatformPrincipal;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Issues and verifies API-signed support-session JWTs. The key is supplied only by deployment configuration. */
@Component
public class SupportSessionTokenService {
    private static final String ISSUER = "edu360-api-support-session";
    private final byte[] secret;

    public record Claims(UUID sessionId, UUID platformUserId, UUID tenantId,
                         UUID ticketId, UUID grantId, UUID institutionId,
                         Instant expiresAt) {}

    public SupportSessionTokenService(
            @Value("${app.support-session.signing-secret:}") String signingSecret) {
        this.secret = signingSecret == null ? new byte[0] : signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(SupportSession session, SupportAccessGrant grant, PlatformPrincipal actor) {
        requireSigningKey();
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(actor.platformUserId().toString())
                    .issueTime(Date.from(session.getIssuedAt()))
                    .expirationTime(Date.from(session.getExpiresAt()))
                    .claim("typ", "support")
                    .claim("scope", "READ")
                    .claim("session_id", session.getSupportSessionId().toString())
                    .claim("tenant_id", grant.getTenantId().toString())
                    .claim("ticket_id", grant.getTicketId().toString())
                    .claim("grant_id", grant.getSupportAccessGrantId().toString());
            if (grant.getInstitutionId() != null) {
                claims.claim("institution_id", grant.getInstitutionId().toString());
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not issue support session token.", ex);
        }
    }

    /** Detects only the token type; the result is untrusted until verify() succeeds. */
    public boolean isSupportToken(String compactToken) {
        try {
            return "support".equals(SignedJWT.parse(compactToken).getJWTClaimsSet().getStringClaim("typ"));
        } catch (Exception ex) {
            return false;
        }
    }

    public Optional<Claims> verify(String compactToken) {
        requireSigningKey();
        try {
            SignedJWT jwt = SignedJWT.parse(compactToken);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(c.getIssuer())
                    || !"support".equals(c.getStringClaim("typ"))
                    || !"READ".equals(c.getStringClaim("scope"))
                    || c.getExpirationTime() == null
                    || !c.getExpirationTime().toInstant().isAfter(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                    UUID.fromString(c.getStringClaim("session_id")),
                    UUID.fromString(c.getSubject()),
                    UUID.fromString(c.getStringClaim("tenant_id")),
                    UUID.fromString(c.getStringClaim("ticket_id")),
                    UUID.fromString(c.getStringClaim("grant_id")),
                    c.getStringClaim("institution_id") == null ? null : UUID.fromString(c.getStringClaim("institution_id")),
                    c.getExpirationTime().toInstant()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void requireSigningKey() {
        if (secret.length < 32) {
            throw new IllegalStateException("SUPPORT_SESSION_SIGNING_SECRET must contain at least 32 UTF-8 bytes.");
        }
    }
}
