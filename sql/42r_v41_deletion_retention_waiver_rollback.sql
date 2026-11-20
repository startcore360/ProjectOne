-- E5-S2 rollback. Refuses to discard recorded retention waivers: they are part of the deletion audit trail.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'platform'
                 AND table_name = 'deletion_request' AND column_name = 'retention_waived_at') THEN
        IF EXISTS (SELECT 1 FROM platform.deletion_request WHERE retention_waived_at IS NOT NULL) THEN
            RAISE EXCEPTION 'E5_S2_WAIVER_DATA_EXISTS; export or reconcile retention waivers before rollback';
        END IF;
    END IF;
END $$;
ALTER TABLE platform.deletion_request DROP CONSTRAINT IF EXISTS ck_deletion_request_retention_waiver;
ALTER TABLE platform.deletion_request DROP COLUMN IF EXISTS retention_waiver_reason;
ALTER TABLE platform.deletion_request DROP COLUMN IF EXISTS retention_waived_at;
ALTER TABLE platform.deletion_request DROP COLUMN IF EXISTS retention_waived_by;
COMMIT;
