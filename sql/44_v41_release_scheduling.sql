-- E7-S1: planned release deployment time. Additive and repeatable.
ALTER TABLE platform.release ADD COLUMN IF NOT EXISTS scheduled_at timestamptz;
COMMENT ON COLUMN platform.release.scheduled_at IS 'Planned deployment instant; scheduling leaves release in STAGING_TESTED (E7-S1).';

-- E7-S3: hourly request aggregates. Route templates only; no raw URL, query string or identifiers.
CREATE TABLE IF NOT EXISTS platform.request_metric_hourly (
    request_metric_hourly_id uuid PRIMARY KEY,
    bucket_start timestamptz NOT NULL,
    route_template varchar(300) NOT NULL,
    status_class varchar(3) NOT NULL,
    request_count bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT uq_request_metric_hourly_bucket_route_status
        UNIQUE (bucket_start, route_template, status_class),
    CONSTRAINT ck_request_metric_hourly_status CHECK (status_class = '5XX'),
    CONSTRAINT ck_request_metric_hourly_count CHECK (request_count > 0),
    CONSTRAINT ck_request_metric_hourly_route CHECK (route_template ~ '^/[A-Za-z0-9_{}/*:.-]+$'),
    CONSTRAINT ck_request_metric_hourly_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_request_metric_hourly_updated CHECK (updated_at >= created_at)
);
CREATE INDEX IF NOT EXISTS ix_request_metric_hourly_bucket
    ON platform.request_metric_hourly (bucket_start);
DROP TRIGGER IF EXISTS trg_touch ON platform.request_metric_hourly;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.request_metric_hourly
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
GRANT SELECT, INSERT, UPDATE ON platform.request_metric_hourly TO edu_platform;
COMMENT ON TABLE platform.request_metric_hourly IS
    'Hourly aggregated HTTP 5xx request counts by matched route template; raw URLs and request identifiers are never stored (E7-S3).';
