-- Rollback for 40_v41_support_session.sql.
DROP TABLE IF EXISTS platform.support_session;
DELETE FROM ref.setting_definition WHERE setting_key = 'support_ticket_categories';
