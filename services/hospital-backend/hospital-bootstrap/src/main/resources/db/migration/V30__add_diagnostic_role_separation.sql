-- Formal clinical duties. This migration creates roles and permissions only; never test accounts.
INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:lab:specimen:list','检验标本查看','API'),
 ('hospital:clinical:lab:specimen:collect','检验标本采集','API'),
 ('hospital:clinical:lab:specimen:receive','检验标本接收','API'),
 ('hospital:clinical:lab:result:entry','检验结果录入','API'),
 ('hospital:clinical:imaging:study:execute','影像检查执行','API'),
 ('hospital:clinical:imaging:report:create','影像报告创建','API');

INSERT INTO sys_role(role_code,role_name,data_scope) VALUES
 ('LAB_COLLECTOR','检验采集员','CUSTOM'),
 ('LAB_TECHNICIAN','检验技术员','CUSTOM'),
 ('LAB_REVIEWER','检验审核员','CUSTOM'),
 ('RADIOLOGY_TECHNICIAN','影像技师','CUSTOM'),
 ('RADIOLOGY_REPORTING_DOCTOR','影像报告医师','CUSTOM'),
 ('EXAMINATION_STAFF','检查工作人员','CUSTOM'),
 ('CLINICAL_RESULT_AUDITOR','临床结果审计员','CUSTOM');

INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:lab:specimen:list','hospital:clinical:lab:specimen:collect') WHERE r.role_code='LAB_COLLECTOR';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:lab:specimen:list','hospital:clinical:lab:specimen:receive','hospital:clinical:lab:result:entry') WHERE r.role_code='LAB_TECHNICIAN';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:lab:specimen:list','hospital:clinical:lab:result:verify') WHERE r.role_code='LAB_REVIEWER';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:imaging:study:execute') WHERE r.role_code='RADIOLOGY_TECHNICIAN';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:imaging:report:create','hospital:clinical:report:sign') WHERE r.role_code='RADIOLOGY_REPORTING_DOCTOR';
