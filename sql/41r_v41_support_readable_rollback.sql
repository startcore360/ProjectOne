-- Rollback for 41_v41_support_readable.sql.
ALTER TABLE ref.action_catalog DROP COLUMN IF EXISTS support_readable;
