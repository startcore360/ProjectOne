-- E5-S2: Product Owner "waive retention wait" on a tenant deletion request (PD-7 exception).
-- The waiver has its own columns so that dpo_remark stays the DPO's decision remark only.
-- All three columns are set together or not at all, the reason is 1-900 characters after trimming,
-- and only a PRODUCT_OWNER-origin request can carry a waiver. Re-runnable.
BEGIN;

ALTER TABLE platform.deletion_request ADD COLUMN IF NOT EXISTS retention_waived_by uuid;
ALTER TABLE platform.deletion_request ADD COLUMN IF NOT EXISTS retention_waived_at timestamptz;
ALTER TABLE platform.deletion_request ADD COLUMN IF NOT EXISTS retention_waiver_reason text;

ALTER TABLE platform.deletion_request DROP CONSTRAINT IF EXISTS ck_deletion_request_retention_waiver;
ALTER TABLE platform.deletion_request ADD CONSTRAINT ck_deletion_request_retention_waiver CHECK (
    (retention_waived_by IS NULL AND retention_waived_at IS NULL AND retention_waiver_reason IS NULL)
    -- retention_waiver_reason IS NOT NULL is explicit: char_length(NULL) is NULL, and a CHECK that evaluates to
    -- NULL passes, so without it a waiver with no reason would be accepted.
    OR (retention_waived_by IS NOT NULL AND retention_waived_at IS NOT NULL AND retention_waiver_reason IS NOT NULL
        AND char_length(btrim(retention_waiver_reason)) BETWEEN 1 AND 900
        AND origin = 'PRODUCT_OWNER'));

COMMENT ON COLUMN platform.deletion_request.retention_waived_by IS
    'E5-S2/PD-7: Product Owner who waived the retention wait when confirming; NULL when not waived.';
COMMENT ON COLUMN platform.deletion_request.retention_waived_at IS
    'E5-S2/PD-7: when the retention wait was waived; NULL when not waived.';
COMMENT ON COLUMN platform.deletion_request.retention_waiver_reason IS
    'E5-S2/PD-7: mandatory reason for the waiver (1-900 chars). Only the retention-date gate is waived; the export READY gate still applies.';

COMMIT;
