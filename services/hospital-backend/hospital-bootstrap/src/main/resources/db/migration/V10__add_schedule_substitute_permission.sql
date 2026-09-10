INSERT INTO sys_permission (permission_code,permission_name,permission_type)
VALUES ('hospital:schedule:substitute','排班替诊','API');

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r JOIN sys_permission p ON p.permission_code='hospital:schedule:substitute'
WHERE r.role_code='SUPER_ADMIN';
