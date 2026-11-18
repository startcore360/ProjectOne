-- E7-S1: planned release deployment time. Additive and repeatable.
ALTER TABLE platform.release ADD COLUMN IF NOT EXISTS scheduled_at timestamptz;
COMMENT ON COLUMN platform.release.scheduled_at IS 'Planned deployment instant; scheduling leaves release in STAGING_TESTED (E7-S1).';
