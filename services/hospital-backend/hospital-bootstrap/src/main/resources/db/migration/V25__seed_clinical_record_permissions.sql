INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:record:view','病历受控查看','API'),
 ('hospital:clinical:record:amend','病历修订','API'),
 ('hospital:clinical:record:audit','病历临床审计只读','API');

INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code='hospital:clinical:record:amend'
 WHERE r.role_code='SUPER_ADMIN';
