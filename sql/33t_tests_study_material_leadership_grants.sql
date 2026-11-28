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
        RAISE EXCEPTION 'STUDY_MATERIAL_VIEW_LEADERSHIP_GRANTS_FAILED';
    END IF;
END$$;
