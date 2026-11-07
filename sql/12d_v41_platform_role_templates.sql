-- EDU 360 V4.1 — one starting template per customisable standard platform role, copied from
-- ref.standard_role_action. Apply after 05_ref_data.sql and 12_v41_platform_roles.sql. Safe to run
-- twice: a template already present, including one a Product Owner has edited, is left unchanged.
-- Sign-in and profile actions are excluded because every account has them.

INSERT INTO platform.platform_role_template
  (platform_role_template_id, template_name, description, base_platform_role, is_standard, is_locked, created_by, updated_by)
SELECT md5('platform-template:' || r.role_code)::uuid,
       r.role_name,
       'Standard starting point for this role.',
       r.role_code,
       true,
       r.role_code = 'DATA_PROTECTION_OFFICER',
       '00000000-0000-0000-0000-000000000000',
       '00000000-0000-0000-0000-000000000000'
FROM ref.standard_role r
WHERE r.role_code IN ('PLATFORM_ADMIN', 'BILLING_MANAGER', 'SUPPORT_MANAGER', 'RELEASE_MANAGER', 'DATA_PROTECTION_OFFICER')
ON CONFLICT DO NOTHING;

INSERT INTO platform.platform_role_template_action
  (platform_role_template_action_id, platform_role_template_id, action_code, created_by)
SELECT md5('platform-template-action:' || s.role_code || ':' || s.action_code)::uuid,
       md5('platform-template:' || s.role_code)::uuid,
       s.action_code,
       '00000000-0000-0000-0000-000000000000'
FROM ref.standard_role_action s
WHERE s.role_code IN ('PLATFORM_ADMIN', 'BILLING_MANAGER', 'SUPPORT_MANAGER', 'RELEASE_MANAGER', 'DATA_PROTECTION_OFFICER')
  AND s.action_code NOT IN ('AUTH_LOGIN', 'AUTH_PASSWORD_CHANGE', 'AUTH_DEVICES_MANAGE', 'PROFILE_EDIT_OWN', 'ROLE_SWITCH')
ON CONFLICT DO NOTHING;
