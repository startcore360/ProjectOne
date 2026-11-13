DELETE FROM ref.standard_role_action
 WHERE action_code='STAFF_RECORD_VIEW'
   AND role_code IN ('HR_MANAGER','HR_EXECUTIVE','UNIVERSITY_HR_MANAGER');
