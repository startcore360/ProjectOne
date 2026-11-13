DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('HR_MANAGER'), ('HR_EXECUTIVE'), ('UNIVERSITY_HR_MANAGER')) r(role_code)
         WHERE NOT EXISTS (SELECT 1 FROM ref.standard_role_action a
                            WHERE a.role_code=r.role_code AND a.action_code='STAFF_RECORD_VIEW')
    ) THEN RAISE EXCEPTION 'STAFF_RECORD_VIEW_HR_GRANTS_FAILED'; END IF;
END$$;
