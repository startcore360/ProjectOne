-- Minimal reference fixture for applying the Sprint 2 migration in the isolated acceptance-test database.
-- The full V3.3 reference load is deliberately not used here because 04_tests.sql owns its own seed rows.
INSERT INTO ref.approval_process VALUES
 ('ATTENDANCE_CORRECTION_COLLEGE','Attendance correction (college)','ATTENDANCE_CORRECTION_REQUEST',true,true,'Sprint 2 test fixture'),
 ('ATTENDANCE_CORRECTION_SCHOOL','Attendance correction (school)','ATTENDANCE_CORRECTION_REQUEST',true,true,'Sprint 2 test fixture')
ON CONFLICT(process_code) DO NOTHING;

INSERT INTO ref.standard_role(role_code,role_name,role_level,introduced_in_version,requires_two_step_login,is_staff) VALUES
 ('PROFESSOR','Professor','COLLEGE','4.0',false,true),
 ('ASSOCIATE_PROFESSOR','Associate Professor','COLLEGE','4.0',false,true),
 ('ASSISTANT_PROFESSOR','Assistant Professor','COLLEGE','4.0',false,true),
 ('HEAD_OF_DEPARTMENT','Head of Department','COLLEGE','4.0',false,true),
 ('CLASS_TEACHER','Class Teacher','SCHOOL','4.0',false,true),
 ('SUBJECT_TEACHER','Subject Teacher','SCHOOL','4.0',false,true),
 ('DEPARTMENT_COORDINATOR','Department Coordinator','COLLEGE','4.0',false,true),
 ('PRINCIPAL_COLLEGE','Principal College','COLLEGE','4.0',false,true),
 ('SCHOOL_PRINCIPAL','School Principal','SCHOOL','4.0',false,true),
 ('VICE_PRINCIPAL_COLLEGE','Vice Principal College','COLLEGE','4.0',false,true),
 ('VICE_PRINCIPAL_SCHOOL','Vice Principal School','SCHOOL','4.0',false,true)
ON CONFLICT(role_code) DO NOTHING;

INSERT INTO ref.action_catalog(action_code,action_no,module_name,phase,description,is_approval_action,sensitive_class,changed_in) VALUES
 ('TIMETABLE_BUILD',600,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ONLINE_PERIOD_SET',601,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('TIMETABLE_VIEW',602,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('SYLLABUS_VIEW',603,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('SYLLABUS_MANAGE',604,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_MARK',605,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_NOT_HELD',606,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('SUBSTITUTE_ASSIGN',607,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_CORRECTION_REQUEST',608,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_CORRECTION_APPROVE',609,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_BULK_UPLOAD',610,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_BULK_UPLOAD_APPROVE',611,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_RULES_SET',612,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_VIEW',613,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ATTENDANCE_VIEW_OWN',614,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ASSIGNMENT_CREATE',615,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ASSIGNMENT_CLOSE',616,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('STUDY_MATERIAL_UPLOAD',617,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ASSIGNMENT_SUBMIT',618,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ASSIGNMENT_EVALUATE',619,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('HOMEWORK_STATUS_MARK',620,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('ASSIGNMENT_VIEW_OWN',621,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('HOMEWORK_COMPLIANCE_VIEW',622,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('STAFF_RECORD_MANAGE',623,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('STAFF_DOCUMENT_VERIFY',624,'Sprint 2',1,'Test fixture action',false,NULL,NULL),
 ('STAFF_RECORD_VIEW',625,'Sprint 2',1,'Test fixture action',false,NULL,NULL)
ON CONFLICT(action_code) DO NOTHING;

INSERT INTO ref.standard_role_action(standard_role_action_id,role_code,action_code,limited_to) VALUES
 (3000,'DEPARTMENT_COORDINATOR','TIMETABLE_BUILD','Test fixture'),
 (3001,'DEPARTMENT_COORDINATOR','ONLINE_PERIOD_SET','Test fixture'),
 (3002,'PROFESSOR','TIMETABLE_VIEW','Test fixture'),
 (3003,'PROFESSOR','SYLLABUS_VIEW','Assigned subjects'),
 (3004,'HEAD_OF_DEPARTMENT','SYLLABUS_MANAGE','Own department'),
 (3005,'PROFESSOR','ATTENDANCE_MARK','Test fixture'),
 (3006,'PROFESSOR','ATTENDANCE_NOT_HELD','Test fixture'),
 (3007,'HEAD_OF_DEPARTMENT','SUBSTITUTE_ASSIGN','Test fixture'),
 (3008,'PROFESSOR','ATTENDANCE_CORRECTION_REQUEST','Test fixture'),
 (3009,'HEAD_OF_DEPARTMENT','ATTENDANCE_CORRECTION_APPROVE','Test fixture'),
 (3010,'PROFESSOR','ATTENDANCE_BULK_UPLOAD','Test fixture'),
 (3011,'PRINCIPAL_COLLEGE','ATTENDANCE_BULK_UPLOAD_APPROVE','Test fixture'),
 (3012,'PRINCIPAL_COLLEGE','ATTENDANCE_RULES_SET','Test fixture'),
 (3013,'PROFESSOR','ATTENDANCE_VIEW','Test fixture'),
 (3014,'PROFESSOR','ATTENDANCE_VIEW_OWN','Test fixture'),
 (3015,'PROFESSOR','ASSIGNMENT_CREATE','Test fixture'),
 (3016,'PROFESSOR','ASSIGNMENT_CLOSE','Test fixture'),
 (3017,'PROFESSOR','STUDY_MATERIAL_UPLOAD','Test fixture'),
 (3018,'PROFESSOR','ASSIGNMENT_SUBMIT','Test fixture'),
 (3019,'PROFESSOR','ASSIGNMENT_EVALUATE','Test fixture'),
 (3020,'CLASS_TEACHER','HOMEWORK_STATUS_MARK','Test fixture'),
 (3021,'PROFESSOR','ASSIGNMENT_VIEW_OWN','Test fixture'),
 (3022,'PRINCIPAL_COLLEGE','HOMEWORK_COMPLIANCE_VIEW','Test fixture'),
 (3023,'PRINCIPAL_COLLEGE','STAFF_RECORD_MANAGE','Test fixture'),
 (3024,'PRINCIPAL_COLLEGE','STAFF_DOCUMENT_VERIFY','Test fixture'),
 (3025,'PRINCIPAL_COLLEGE','STAFF_RECORD_VIEW','Test fixture')
ON CONFLICT(standard_role_action_id) DO NOTHING;
