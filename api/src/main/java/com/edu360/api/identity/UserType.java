package com.edu360.api.identity;

/**
 * WHAT: the six kinds of login identity app.user_account can hold.
 * WHY this exact list and no other values: it mirrors the database's own
 * ck_user_account_user_type CHECK constraint in sql/01_schema.sql word for word - if this enum
 * and that constraint ever drift apart, every insert/update from the app would start failing
 * the database check instead of failing fast in Java, so keep the two in lockstep by hand.
 */
public enum UserType {
    STAFF,
    STUDENT,
    GUARDIAN,
    APPLICANT,
    /** Management or the Governing Body Approver - deliberately not "staff" (schema comment,
     *  V4.0 decision C9): these two roles sit above and outside the normal staff hierarchy. */
    GOVERNANCE,
    /** The reserved system/job actor used as created_by/updated_by when no human did the
     *  action (e.g. scheduled jobs, bootstrap rows) - never a real login. */
    SYSTEM
}
