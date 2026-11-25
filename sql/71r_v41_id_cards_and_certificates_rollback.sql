-- Destructive rollback for migration 71; use only when T9 card/certificate data may be discarded.
BEGIN;
DROP TABLE IF EXISTS app.certificate_issuance;
DROP TABLE IF EXISTS app.certificate_request;
DROP TABLE IF EXISTS app.certificate_template;
DROP TABLE IF EXISTS app.id_card_replacement;
DROP TABLE IF EXISTS app.id_card_issuance;
DROP TABLE IF EXISTS app.id_card_template;
COMMIT;
