-- STEP 7.4B: formal examination execution duties.  This migration creates no users or credentials.
INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:examination:order:list','检查申请单查看','API'),
 ('hospital:clinical:examination:accept','检查申请接单','API'),
 ('hospital:clinical:examination:start','检查执行开始','API'),
 ('hospital:clinical:examination:complete','检查执行完成','API'),
 ('hospital:clinical:examination:report:create','检查报告创建','API');

INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN (
  'hospital:clinical:examination:order:list',
  'hospital:clinical:examination:accept',
  'hospital:clinical:examination:start',
  'hospital:clinical:examination:complete',
  'hospital:clinical:examination:report:create',
  'hospital:clinical:report:sign'
 ) WHERE r.role_code='EXAMINATION_STAFF';
