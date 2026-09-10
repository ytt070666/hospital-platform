-- STEP 7.4D: a clinical-result auditor is deliberately read-only.  The role
-- existed in V30 but had no permission, which made its audit boundary
-- impossible to exercise through the production authorization path.
INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:result:audit','临床诊断结果审计只读','API');

INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p
   ON p.permission_code='hospital:clinical:result:audit'
 WHERE r.role_code='CLINICAL_RESULT_AUDITOR';
