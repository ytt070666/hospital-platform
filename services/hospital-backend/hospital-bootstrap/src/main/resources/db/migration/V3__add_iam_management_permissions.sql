INSERT INTO sys_permission (permission_code, permission_name, permission_type) VALUES
('system:user:update','编辑用户','API'),('system:role:create','创建角色','API');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('system:user:update','system:role:create') WHERE r.role_code='SUPER_ADMIN';
