-- C2-6: complete the approved STUDY_MATERIAL_VIEW role grants.
-- Existing tenant role copies are intentionally not modified (OD-62).
WITH desired(role_code, limited_to) AS (
    VALUES
      ('PRINCIPAL_COLLEGE', 'Own institution study material'),
      ('VICE_PRINCIPAL_COLLEGE', 'Own institution study material'),
      ('SCHOOL_PRINCIPAL', 'Own institution study material'),
      ('VICE_PRINCIPAL_SCHOOL', 'Own institution study material')
), missing AS (
    SELECT d.role_code, d.limited_to
      FROM desired d
      JOIN ref.standard_role r ON r.role_code = d.role_code
     WHERE NOT EXISTS (
        SELECT 1 FROM ref.standard_role_action a
         WHERE a.role_code = d.role_code
           AND a.action_code = 'STUDY_MATERIAL_VIEW'
     )
), numbered AS (
    SELECT (SELECT COALESCE(MAX(standard_role_action_id), 0) FROM ref.standard_role_action)
           + row_number() OVER (ORDER BY role_code) AS id,
           role_code, limited_to
      FROM missing
)
INSERT INTO ref.standard_role_action(standard_role_action_id, role_code, action_code, limited_to)
SELECT id, role_code, 'STUDY_MATERIAL_VIEW', limited_to
  FROM numbered;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM (VALUES ('PRINCIPAL_COLLEGE'), ('VICE_PRINCIPAL_COLLEGE'),
                       ('SCHOOL_PRINCIPAL'), ('VICE_PRINCIPAL_SCHOOL')) required(role_code)
         WHERE NOT EXISTS (
             SELECT 1 FROM ref.standard_role_action a
              WHERE a.role_code = required.role_code
                AND a.action_code = 'STUDY_MATERIAL_VIEW'
         )
    ) THEN
        RAISE EXCEPTION 'STUDY_MATERIAL_VIEW_ROLE_GRANTS_MISSING';
    END IF;
END$$;
