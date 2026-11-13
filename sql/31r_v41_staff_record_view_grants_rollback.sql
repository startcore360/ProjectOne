-- Rollback for 31_v41_staff_record_view_grants.sql
DELETE FROM ref.standard_role_action
 WHERE id IN (9001,9002,9003)
   AND action_code = 'STAFF_RECORD_VIEW';
