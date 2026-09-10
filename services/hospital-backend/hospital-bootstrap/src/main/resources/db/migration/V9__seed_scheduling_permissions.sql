INSERT INTO sys_permission (permission_code,permission_name,permission_type) VALUES
('hospital:schedule:list','查看排班','API'),
('hospital:schedule:create','创建排班','API'),
('hospital:schedule:update','编辑排班','API'),
('hospital:schedule:publish','发布排班','API'),
('hospital:schedule:stop','停诊与替诊','API'),
('hospital:schedule:quota','调整排班号源','API'),
('hospital:schedule-template:list','查看排班模板','API'),
('hospital:schedule-template:manage','管理排班模板','API'),
('hospital:schedule-session:manage','管理门诊班次','API');

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code LIKE 'hospital:schedule%' WHERE r.role_code='SUPER_ADMIN';

INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'schedule-session','门诊班次','/schedule/sessions','SchedulingWorkbench',30 FROM sys_permission WHERE permission_code='hospital:schedule-session:manage';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'schedule-template','排班模板','/schedule/templates','SchedulingWorkbench',31 FROM sys_permission WHERE permission_code='hospital:schedule-template:list';
INSERT INTO sys_menu (permission_id,menu_code,menu_name,route_path,component,sort_order)
SELECT id,'schedule-calendar','排班日历','/schedule/calendar','SchedulingWorkbench',32 FROM sys_permission WHERE permission_code='hospital:schedule:list';
