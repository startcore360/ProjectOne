-- C2-2 / B4: HR operators who manage staff records also need the read action
-- used by the directory and document-list endpoints.
WITH desired(role_code, limited_to) AS (
    VALUES
      ('HR_MANAGER', 'Own institution / department scope'),
      ('HR_EXECUTIVE', 'Own institution / department scope'),
      ('UNIVERSITY_HR_MANAGER', 'Own university scope')
), missing AS (
    SELECT d.role_code, d.limited_to
      FROM desired d
      JOIN ref.standard_role r ON r.role_code = d.role_code
     WHERE NOT EXISTS (
        SELECT 1 FROM ref.standard_role_action a
         WHERE a.role_code = d.role_code AND a.action_code = 'STAFF_RECORD_VIEW'
     )
), numbered AS (
    SELECT (SELECT COALESCE(MAX(standard_role_action_id), 0) FROM ref.standard_role_action)
           + row_number() OVER (ORDER BY role_code) AS id,
           role_code, limited_to
      FROM missing
)
INSERT INTO ref.standard_role_action(standard_role_action_id, role_code, action_code, limited_to)
SELECT id, role_code, 'STAFF_RECORD_VIEW', limited_to FROM numbered;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('HR_MANAGER'), ('HR_EXECUTIVE'), ('UNIVERSITY_HR_MANAGER')) r(role_code)
         WHERE NOT EXISTS (SELECT 1 FROM ref.standard_role_action a
                            WHERE a.role_code=r.role_code AND a.action_code='STAFF_RECORD_VIEW')
    ) THEN RAISE EXCEPTION 'STAFF_RECORD_VIEW_HR_GRANTS_MISSING'; END IF;
END$$;
