INSERT INTO sys_role (role_code, role_name, data_scope) VALUES ('SUPER_ADMIN', '平台超级管理员', 'ALL');
INSERT INTO sys_permission (permission_code, permission_name, permission_type) VALUES
('system:user:list','查看用户','API'),('system:user:create','创建用户','API'),('system:user:disable','停用用户','API'),('system:role:list','查看角色','API'),('system:role:update','更新角色权限','API'),('system:permission:list','查看权限','API'),('system:menu:list','查看菜单','API'),('system:organization:list','查看组织','API'),('system:file:upload','上传文件','API'),('system:file:read','读取文件','API'),('system:event:publish','发布系统测试事件','API');
INSERT INTO sys_role_permission (role_id, permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='SUPER_ADMIN';
INSERT INTO sys_menu (menu_code, menu_name, route_path, component, sort_order) VALUES ('dashboard','工作台','/','Dashboard',1);
INSERT INTO sys_menu (permission_id, menu_code, menu_name, route_path, component, sort_order) SELECT id,'users','用户管理','/system/users','SystemUsers',10 FROM sys_permission WHERE permission_code='system:user:list';
INSERT INTO sys_menu (permission_id, menu_code, menu_name, route_path, component, sort_order) SELECT id,'roles','角色管理','/system/roles','SystemRoles',11 FROM sys_permission WHERE permission_code='system:role:list';
INSERT INTO sys_menu (permission_id, menu_code, menu_name, route_path, component, sort_order) SELECT id,'permissions','权限管理','/system/permissions','SystemPermissions',12 FROM sys_permission WHERE permission_code='system:permission:list';
INSERT INTO sys_menu (permission_id, menu_code, menu_name, route_path, component, sort_order) SELECT id,'menus','菜单管理','/system/menus','SystemMenus',13 FROM sys_permission WHERE permission_code='system:menu:list';
INSERT INTO sys_menu (permission_id, menu_code, menu_name, route_path, component, sort_order) SELECT id,'organizations','组织机构','/system/organizations','SystemOrganizations',14 FROM sys_permission WHERE permission_code='system:organization:list';
