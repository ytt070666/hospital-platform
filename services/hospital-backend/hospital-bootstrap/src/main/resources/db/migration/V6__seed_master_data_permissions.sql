INSERT INTO sys_permission (permission_code,permission_name,permission_type) VALUES
('hospital:info:list','查看医院信息','API'),('hospital:info:update','编辑医院信息','API'),
('hospital:campus:list','查看院区','API'),('hospital:campus:manage','管理院区楼栋楼层','API'),
('hospital:department:list','查看医疗科室','API'),('hospital:department:manage','管理医疗科室','API'),
('hospital:doctor:list','查看医生','API'),('hospital:doctor:manage','管理医生','API'),('hospital:doctor:publish','发布医生','API'),
('hospital:title:manage','管理医生职称','API'),('hospital:specialty:manage','管理专业领域','API'),('hospital:clinic-type:manage','管理门诊类型','API');
INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code LIKE 'hospital:%' WHERE r.role_code='SUPER_ADMIN';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-hospital','医院主数据','/master/hospital','MasterData',20 FROM sys_permission WHERE permission_code='hospital:info:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-campus','院区楼宇','/master/campuses','MasterData',21 FROM sys_permission WHERE permission_code='hospital:campus:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-department','医疗科室','/master/departments','MasterData',22 FROM sys_permission WHERE permission_code='hospital:department:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-doctor','医生管理','/master/doctors','MasterData',23 FROM sys_permission WHERE permission_code='hospital:doctor:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'master-dictionary','职称与门诊类型','/master/dictionaries','MasterData',24 FROM sys_permission WHERE permission_code='hospital:title:manage';
