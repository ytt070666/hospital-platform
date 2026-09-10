INSERT INTO sys_permission (permission_code,permission_name,permission_type) VALUES
('hospital:patient:list','查询患者','API'),
('hospital:patient:view','查看患者详情','API'),
('hospital:patient:disable','禁用患者','API'),
('hospital:patient:member:list','查询就诊人','API'),
('hospital:patient:verification:view','查询实名状态','API');

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code LIKE 'hospital:patient:%' WHERE r.role_code='SUPER_ADMIN';

INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'patient-management','患者管理','/patients','PatientManagement',40 FROM sys_permission WHERE permission_code='hospital:patient:list';
