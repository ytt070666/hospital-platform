INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:fee:list','挂号费规则查询','API'),
 ('hospital:fee:create','挂号费规则新增','API'),
 ('hospital:fee:update','挂号费规则编辑','API'),
 ('hospital:payment:list','挂号与支付订单查询','API'),
 ('hospital:payment:view','挂号与支付订单详情','API');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN
 ('hospital:fee:list','hospital:fee:create','hospital:fee:update','hospital:payment:list','hospital:payment:view');
INSERT INTO sys_menu(permission_id,menu_code,menu_name,route_path,component,sort_order)
 SELECT id,'registration-fees','挂号费规则','/registration-fees','RegistrationFees',42 FROM sys_permission WHERE permission_code='hospital:fee:list';
INSERT INTO sys_menu(permission_id,menu_code,menu_name,route_path,component,sort_order)
 SELECT id,'payment-orders','挂号与支付订单','/payment-orders','PaymentOrders',43 FROM sys_permission WHERE permission_code='hospital:payment:list';
