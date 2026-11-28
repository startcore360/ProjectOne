-- Rollback for 44_v41_release_scheduling.sql.
DROP TABLE IF EXISTS platform.request_metric_hourly;
ALTER TABLE platform.release DROP COLUMN IF EXISTS scheduled_at;
