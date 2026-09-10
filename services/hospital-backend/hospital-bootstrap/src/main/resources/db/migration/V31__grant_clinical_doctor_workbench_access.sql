-- A clinical order doctor must use the existing doctor workbench; this grants no administrative scope.
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code='hospital:visit:doctor:workbench'
WHERE r.role_code='CLINICAL_DOCTOR';
