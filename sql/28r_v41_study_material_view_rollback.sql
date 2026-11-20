-- Roll back B5 study-material view action only after tenant role-action rollout is reversed.
DELETE FROM ref.standard_role_action WHERE action_code='STUDY_MATERIAL_VIEW';
DELETE FROM ref.action_catalog WHERE action_code='STUDY_MATERIAL_VIEW';
