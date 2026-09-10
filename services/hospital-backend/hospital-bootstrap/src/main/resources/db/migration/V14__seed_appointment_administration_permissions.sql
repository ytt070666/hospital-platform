INSERT INTO sys_permission (permission_code,permission_name,permission_type) VALUES
('hospital:appointment:list','查询预约','API'),
('hospital:appointment:view','查看预约详情','API'),
('hospital:appointment:cancel','协助取消预约','API'),
('hospital:appointment:history','查看预约历史','API');

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code LIKE 'hospital:appointment:%' WHERE r.role_code='SUPER_ADMIN';

INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'appointment-management','预约管理','/appointments','AppointmentManagement',41 FROM sys_permission WHERE permission_code='hospital:appointment:list';
