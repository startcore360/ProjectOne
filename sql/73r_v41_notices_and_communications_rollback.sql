BEGIN;
DROP TABLE IF EXISTS app.message_dispatch;
DROP TABLE IF EXISTS app.message_template;
DROP TABLE IF EXISTS app.communication_campaign;
DROP TABLE IF EXISTS app.notice_acknowledgment;
DROP TABLE IF EXISTS app.notice_target;
DROP TABLE IF EXISTS app.notice_bulletin;
COMMIT;
