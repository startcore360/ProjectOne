# Emergency reset of two-step login (decision MFA-01)

Use this **only** when nothing in the app can help:

- the person has lost their authenticator **and** their backup codes, and
- nobody above them can use **Reset two-step login** (in practice: the Product Owner).

Everyone else is reset from the app:

- school or college users by their admin;
- platform staff by the Product Owner.

## Who does it

Two different people:

- **Operator:** the person who runs the servers and holds the Keycloak admin login.
- **Checker:** the Data Protection Officer. They confirm it afterwards.

The person being reset must never do it themselves.

## Before you start

1. **Confirm who is asking.** Use a channel the person already had before the request: a video call with their face, or in person. Never act on an email or message alone.
2. **Write down** the date, the person, how you confirmed them, and why.

## Steps (about 5 minutes)

1. Open the Keycloak admin console. Local: `http://127.0.0.1:8081/admin`.
2. Pick the realm:
   - `edu360-platform` for the Product Owner or platform staff;
   - the school's realm otherwise.
3. Go to **Users** and find the person by email.
4. Open the **Credentials** tab and **delete only the OTP (authenticator) entry.** Never touch the password.
5. Open the **Sessions** tab and **sign out all sessions.**
6. Remove their old backup codes, as the database administrator:
   ```sql
   DELETE FROM platform.mfa_recovery_code
    WHERE realm = '<realm>' AND keycloak_user_id = '<Keycloak user id from step 3>';
   ```
7. Tell the person: "Sign in with your password. The app will show a new QR code. Save the new backup codes it gives you."

## Afterwards (the same day)

1. Send the checker the note from "Before you start", plus the time of each step.
2. The checker confirms in writing that:
   - the request was genuine;
   - only the authenticator was removed;
   - the person has set up a new one.
3. Keep the note with the platform audit records.

## If something looks wrong

If you can't confirm who is asking, **stop.** The account stays locked until you can. A locked account is safer than one taken over by someone else.
