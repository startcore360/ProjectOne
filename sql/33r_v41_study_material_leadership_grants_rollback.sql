-- Roll back only the four leadership grants introduced by migration 33.
DELETE FROM ref.standard_role_action
 WHERE action_code = 'STUDY_MATERIAL_VIEW'
   AND role_code IN ('PRINCIPAL_COLLEGE', 'VICE_PRINCIPAL_COLLEGE',
                     'SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL_SCHOOL');
