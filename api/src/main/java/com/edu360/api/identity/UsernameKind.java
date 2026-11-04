package com.edu360.api.identity;

/** Which field a user types to log in with - mirrors ck_user_identity_link_username_kind.
 *  Staff/governance use EMAIL, students use their Student ID (STUDENT_CODE), parents use
 *  MOBILE (AUTH_LOGIN story, §8.1). Drives which field AuthService validates against, once
 *  that service is migrated (step 7). */
public enum UsernameKind {
    EMAIL,
    STUDENT_CODE,
    MOBILE
}
