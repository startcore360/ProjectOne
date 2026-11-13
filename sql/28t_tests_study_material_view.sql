-- B5: approved study-material read action and standard-role grants.
SELECT public.expect_ok('S2-B5-01','STUDY_MATERIAL_VIEW action is registered and mapped',$q$
 DO $d$ BEGIN
   IF NOT EXISTS (SELECT 1 FROM ref.action_catalog WHERE action_code='STUDY_MATERIAL_VIEW'
                  AND module_name='Assignments and homework' AND phase=1) THEN
     RAISE EXCEPTION 'STUDY_MATERIAL_VIEW_ACTION_MISSING';
   END IF;
   IF NOT EXISTS (SELECT 1 FROM ref.action_module_map
                  WHERE action_module_name='Assignments and homework' AND module_code='ASSIGNMENTS') THEN
     RAISE EXCEPTION 'STUDY_MATERIAL_VIEW_MODULE_MAPPING_MISSING';
   END IF;
 END $d$
$q$);
SELECT public.expect_ok('S2-B5-02','students, active guardians and assigned teaching roles receive view grant',$q$
 DO $d$ DECLARE n int; BEGIN
   SELECT count(*) INTO n FROM ref.standard_role_action
    WHERE action_code='STUDY_MATERIAL_VIEW'
      AND role_code IN ('PROFESSOR','ASSOCIATE_PROFESSOR','ASSISTANT_PROFESSOR','HEAD_OF_DEPARTMENT',
                        'CLASS_TEACHER','SUBJECT_TEACHER','STUDENT_COLLEGE','STUDENT_SCHOOL',
                        'PARENT_COLLEGE','PARENT_SCHOOL');
   IF n <> 10 THEN RAISE EXCEPTION 'STUDY_MATERIAL_VIEW_GRANTS_INCOMPLETE: %', n; END IF;
 END $d$
$q$);
