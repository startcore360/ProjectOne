-- AC-S5 (V3.3): reference counts
SELECT 'actions', count(*), 279 FROM ref.action_catalog
UNION ALL SELECT 'phase1 actions', count(*), 235 FROM ref.action_catalog WHERE phase = 1
UNION ALL SELECT 'phase2 actions', count(*), 32 FROM ref.action_catalog WHERE phase = 2
UNION ALL SELECT 'phase3 actions', count(*), 12 FROM ref.action_catalog WHERE phase = 3
UNION ALL SELECT 'roles', count(*), 71 FROM ref.standard_role
UNION ALL SELECT 'feature areas', count(*), 28 FROM ref.feature_area
UNION ALL SELECT 'modules', count(*), 29 FROM ref.module_catalog
UNION ALL SELECT 'matrix cells', count(*), 1988 FROM ref.permission_matrix_cell
UNION ALL SELECT 'sensitive actions', count(*), 101 FROM ref.action_catalog WHERE sensitive_class IS NOT NULL
UNION ALL SELECT 'role-action pairs', count(*), 1851 FROM ref.standard_role_action
UNION ALL SELECT 'chain steps referencing a non-approval action', count(*), 0 FROM ref.approval_chain_step s JOIN ref.action_catalog a USING (action_code) WHERE a.phase <> 1;
