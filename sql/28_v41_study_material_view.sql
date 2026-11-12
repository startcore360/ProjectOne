-- B5: dedicated read permission for study materials (students, active guardians and assigned staff).
INSERT INTO ref.action_catalog
    (action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in)
VALUES
    ('STUDY_MATERIAL_VIEW', 281, 'Assignments and homework', 1,
     'View study material for assigned subjects or own active enrollment/children',
     false, NULL, 'Sprint 2 B5')
ON CONFLICT (action_code) DO NOTHING;

INSERT INTO ref.action_module_map(action_module_name, module_code)
VALUES ('Assignments and homework', 'ASSIGNMENTS')
ON CONFLICT (action_module_name) DO NOTHING;

WITH desired(role_code, limited_to) AS (
    VALUES
      ('PROFESSOR', 'Assigned subjects'),
      ('ASSOCIATE_PROFESSOR', 'Assigned subjects'),
      ('ASSISTANT_PROFESSOR', 'Assigned subjects'),
      ('HEAD_OF_DEPARTMENT', 'Own department / assigned subjects'),
      ('CLASS_TEACHER', 'Own classes'),
      ('SUBJECT_TEACHER', 'Assigned subjects'),
      ('STUDENT_COLLEGE', 'Own active enrollment'),
      ('STUDENT_SCHOOL', 'Own active enrollment'),
      ('PARENT_COLLEGE', 'Own children with active enrollment'),
      ('PARENT_SCHOOL', 'Own children with active enrollment')
), missing AS (
    SELECT d.role_code, d.limited_to
    FROM desired d
    JOIN ref.standard_role r ON r.role_code = d.role_code
    WHERE NOT EXISTS (
        SELECT 1 FROM ref.standard_role_action a
        WHERE a.role_code = d.role_code AND a.action_code = 'STUDY_MATERIAL_VIEW'
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
