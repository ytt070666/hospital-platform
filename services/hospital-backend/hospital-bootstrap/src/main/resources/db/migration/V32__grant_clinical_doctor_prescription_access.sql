-- A clinical doctor must be able to use the prescription pane already present in the doctor workbench.
-- Medication safety override remains deliberately separate.
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'hospital:clinical:prescription:create',
  'hospital:clinical:prescription:view',
  'hospital:clinical:prescription:sign',
  'hospital:clinical:prescription:cancel'
)
WHERE r.role_code='CLINICAL_DOCTOR';
