INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:finance:dashboard','财务工作台','API'),
 ('hospital:registration-order:list','挂号订单查询','API'),
 ('hospital:registration-order:view','挂号订单详情','API'),
 ('hospital:refund:retry','退款重试','API'),
 ('hospital:reconciliation:view','支付对账详情','API');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN
 ('hospital:finance:dashboard','hospital:registration-order:list','hospital:registration-order:view','hospital:refund:retry','hospital:reconciliation:view');
INSERT INTO sys_menu(permission_id,menu_code,menu_name,route_path,component,sort_order)
 SELECT id,'finance-center','财务订单中心','/finance-center','FinanceCenter',44 FROM sys_permission WHERE permission_code='hospital:finance:dashboard';
