-- STEP 7.4A additive hardening only. V28 remains immutable.
ALTER TABLE diagnostic_report
  ADD COLUMN release_policy VARCHAR(32) NOT NULL DEFAULT 'IMMEDIATE_AFTER_FINAL' AFTER report_type;

CREATE TABLE clinical_execution_user_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  service_type VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_clinical_execution_user_scope (user_id, department_id, service_type),
  KEY idx_clinical_execution_scope_lookup (user_id, service_type, department_id),
  CONSTRAINT fk_clinical_execution_scope_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_clinical_execution_scope_department FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Stable TEST-only catalog codes used by the Test LIS/RIS/PACS contracts and acceptance suites.
UPDATE clinical_service_catalog
SET service_code='TEST_LAB_001', service_name='TEST LAB PANEL', execution_department_id=NULL
WHERE service_code='TEST_LAB_PANEL';
UPDATE clinical_service_catalog
SET service_code='TEST_EXAM_001', service_name='TEST ECG EXAMINATION', execution_department_id=NULL
WHERE service_code='TEST_ECG';
UPDATE clinical_service_catalog
SET service_code='TEST_IMAGING_001', service_name='TEST CHEST X-RAY', execution_department_id=NULL
WHERE service_code='TEST_CHEST_XR';
INSERT INTO clinical_service_catalog(service_code,service_name,service_type,execution_department_id,specimen_type,body_part_required,laterality_required,report_type,effective_from)
SELECT 'TEST_LAB_CRITICAL','TEST CRITICAL LAB','LAB',NULL,'WHOLE_BLOOD',0,0,'LAB',CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM clinical_service_catalog WHERE service_code='TEST_LAB_CRITICAL');
