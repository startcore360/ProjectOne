-- Rollback for 44_v41_release_scheduling.sql.
ALTER TABLE platform.release DROP COLUMN IF EXISTS scheduled_at;
