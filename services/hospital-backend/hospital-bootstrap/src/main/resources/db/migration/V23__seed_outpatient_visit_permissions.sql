INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinic-room:list','诊室查询','API'),
 ('hospital:clinic-room:manage','诊室管理','API'),
 ('hospital:visit:queue:list','候诊队列查询','API'),
 ('hospital:visit:checkin','工作人员签到','API'),
 ('hospital:visit:payment-override','签到缴费豁免','API'),
 ('hospital:visit:queue:call','叫号与候诊操作','API'),
 ('hospital:visit:doctor:workbench','医生工作台','API');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN
 ('hospital:clinic-room:list','hospital:clinic-room:manage','hospital:visit:queue:list','hospital:visit:checkin','hospital:visit:payment-override','hospital:visit:queue:call','hospital:visit:doctor:workbench');
INSERT INTO sys_menu(permission_id,menu_code,menu_name,route_path,component,sort_order)
 SELECT id,'outpatient-visit','门诊候诊工作台','/outpatient-visit','OutpatientVisitCenter',45 FROM sys_permission WHERE permission_code='hospital:visit:queue:list';
