-- Staff managers must be able to read scoped staff profiles to manage records and maintain assignments.
-- Keep document verification as a separate, independently granted action.
INSERT INTO ref.standard_role_action VALUES (9001,'HR_MANAGER','STAFF_RECORD_VIEW','Own scope') ON CONFLICT DO NOTHING;
INSERT INTO ref.standard_role_action VALUES (9002,'HR_EXECUTIVE','STAFF_RECORD_VIEW','Own scope') ON CONFLICT DO NOTHING;
INSERT INTO ref.standard_role_action VALUES (9003,'UNIVERSITY_HR_MANAGER','STAFF_RECORD_VIEW','Own scope') ON CONFLICT DO NOTHING;
