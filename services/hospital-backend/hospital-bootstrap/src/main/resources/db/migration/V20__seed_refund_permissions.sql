INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:refund:list','退款订单查询','API'),
 ('hospital:refund:view','退款订单详情','API'),
 ('hospital:refund:create','退款订单发起','API'),
 ('hospital:reconciliation:list','支付对账异常查询','API');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN
 ('hospital:refund:list','hospital:refund:view','hospital:refund:create','hospital:reconciliation:list');
