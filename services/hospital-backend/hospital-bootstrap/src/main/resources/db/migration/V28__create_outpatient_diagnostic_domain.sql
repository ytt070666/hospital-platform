CREATE TABLE clinical_service_catalog (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_code VARCHAR(64) NOT NULL, service_name VARCHAR(256) NOT NULL, service_type VARCHAR(32) NOT NULL,
  execution_department_id BIGINT NULL, specimen_type VARCHAR(64) NULL, body_part_required TINYINT NOT NULL DEFAULT 0,
  laterality_required TINYINT NOT NULL DEFAULT 0, report_type VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  effective_from DATE NOT NULL, effective_to DATE NULL, version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_clinical_service_code (service_code), KEY idx_clinical_service_search (service_type,status,effective_from)
);
CREATE TABLE clinical_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no VARCHAR(48) NOT NULL, encounter_id BIGINT NOT NULL, patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL, department_id BIGINT NOT NULL, medical_record_revision_id BIGINT NOT NULL,
  order_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', priority VARCHAR(16) NOT NULL DEFAULT 'ROUTINE',
  clinical_indication VARCHAR(2048) NOT NULL, diagnosis_summary_snapshot VARCHAR(1024) NULL, source VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  signed_by BIGINT NULL, signed_at DATETIME(3) NULL, content_hash CHAR(64) NULL, accepted_at DATETIME(3) NULL, started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL, cancelled_at DATETIME(3) NULL, cancel_reason VARCHAR(512) NULL, provider_submission_key VARCHAR(96) NULL,
  version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_clinical_order_no (order_no), UNIQUE KEY uk_clinical_order_provider_key (provider_submission_key),
  KEY idx_clinical_order_encounter (encounter_id,status), KEY idx_clinical_order_patient (patient_id,status), KEY idx_clinical_order_doctor (doctor_id,status),
  CONSTRAINT fk_clinical_order_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id),
  CONSTRAINT fk_clinical_order_revision FOREIGN KEY (medical_record_revision_id) REFERENCES outpatient_medical_record_revision(id)
);
CREATE TABLE clinical_order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, clinical_order_id BIGINT NOT NULL, service_item_id BIGINT NOT NULL,
  item_code_snapshot VARCHAR(64) NOT NULL, item_name_snapshot VARCHAR(256) NOT NULL, category_snapshot VARCHAR(32) NOT NULL,
  specimen_type_snapshot VARCHAR(64) NULL, body_part VARCHAR(128) NULL, laterality VARCHAR(16) NULL, priority VARCHAR(16) NOT NULL,
  instructions VARCHAR(1024) NULL, status VARCHAR(32) NOT NULL DEFAULT 'PLACED', sort_order INT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_clinical_order_item_service (clinical_order_id,service_item_id), KEY idx_clinical_order_item_order (clinical_order_id),
  CONSTRAINT fk_clinical_order_item_order FOREIGN KEY (clinical_order_id) REFERENCES clinical_order(id),
  CONSTRAINT fk_clinical_order_item_service FOREIGN KEY (service_item_id) REFERENCES clinical_service_catalog(id)
);
CREATE TABLE lab_specimen (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, specimen_no VARCHAR(48) NOT NULL, clinical_order_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
  specimen_type VARCHAR(64) NOT NULL, container_type VARCHAR(64) NULL, collection_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_COLLECTION',
  collected_by BIGINT NULL, collected_at DATETIME(3) NULL, received_by BIGINT NULL, received_at DATETIME(3) NULL,
  rejected_by BIGINT NULL, rejected_at DATETIME(3) NULL, rejection_reason_code VARCHAR(64) NULL, rejection_reason VARCHAR(512) NULL,
  replaces_specimen_id BIGINT NULL, version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_lab_specimen_no (specimen_no), KEY idx_lab_specimen_order (clinical_order_id,collection_status), KEY idx_lab_specimen_patient (patient_id,collection_status),
  CONSTRAINT fk_lab_specimen_order FOREIGN KEY (clinical_order_id) REFERENCES clinical_order(id), CONSTRAINT fk_lab_specimen_replaces FOREIGN KEY (replaces_specimen_id) REFERENCES lab_specimen(id)
);
CREATE TABLE diagnostic_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, report_no VARCHAR(48) NOT NULL, clinical_order_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, encounter_id BIGINT NOT NULL,
  report_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', current_revision_id BIGINT NULL, issued_at DATETIME(3) NULL,
  released_at DATETIME(3) NULL, version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_diagnostic_report_no (report_no), UNIQUE KEY uk_diagnostic_report_order (clinical_order_id), KEY idx_diagnostic_report_patient (patient_id,status), KEY idx_diagnostic_report_encounter (encounter_id,status)
);
CREATE TABLE diagnostic_report_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, report_id BIGINT NOT NULL, revision_no INT NOT NULL, parent_revision_id BIGINT NULL, revision_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', findings TEXT NOT NULL, impression TEXT NULL, conclusion_text TEXT NULL, structured_payload JSON NULL,
  signed_by BIGINT NULL, signed_at DATETIME(3) NULL, content_hash CHAR(64) NULL, amend_reason VARCHAR(512) NULL, version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_diagnostic_report_revision (report_id,revision_no), KEY idx_diagnostic_report_revision_status (report_id,status),
  CONSTRAINT fk_diagnostic_revision_report FOREIGN KEY (report_id) REFERENCES diagnostic_report(id), CONSTRAINT fk_diagnostic_revision_parent FOREIGN KEY (parent_revision_id) REFERENCES diagnostic_report_revision(id)
);
ALTER TABLE diagnostic_report ADD CONSTRAINT fk_diagnostic_report_current_revision FOREIGN KEY (current_revision_id) REFERENCES diagnostic_report_revision(id);
CREATE TABLE lab_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, result_no VARCHAR(48) NOT NULL, provider_event_id VARCHAR(96) NOT NULL, clinical_order_id BIGINT NOT NULL, specimen_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PRELIMINARY', performed_at DATETIME(3) NULL, verified_by BIGINT NULL, verified_at DATETIME(3) NULL, report_id BIGINT NULL,
  version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_lab_result_no (result_no), UNIQUE KEY uk_lab_result_event (provider_event_id), UNIQUE KEY uk_lab_result_specimen (specimen_id), KEY idx_lab_result_order (clinical_order_id,status),
  CONSTRAINT fk_lab_result_order FOREIGN KEY (clinical_order_id) REFERENCES clinical_order(id), CONSTRAINT fk_lab_result_specimen FOREIGN KEY (specimen_id) REFERENCES lab_specimen(id), CONSTRAINT fk_lab_result_report FOREIGN KEY (report_id) REFERENCES diagnostic_report(id)
);
CREATE TABLE lab_result_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, lab_result_id BIGINT NOT NULL, test_code VARCHAR(64) NOT NULL, test_name_snapshot VARCHAR(256) NOT NULL, value_type VARCHAR(32) NOT NULL,
  numeric_value DECIMAL(20,6) NULL, text_value VARCHAR(2048) NULL, unit_code VARCHAR(32) NULL, unit_name_snapshot VARCHAR(64) NULL,
  reference_low DECIMAL(20,6) NULL, reference_high DECIMAL(20,6) NULL, reference_text VARCHAR(256) NULL, abnormal_flag VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
  critical_flag TINYINT NOT NULL DEFAULT 0, result_status VARCHAR(32) NOT NULL DEFAULT 'PRELIMINARY', verified_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_lab_result_item_result (lab_result_id), CONSTRAINT fk_lab_result_item_result FOREIGN KEY (lab_result_id) REFERENCES lab_result(id)
);
CREATE TABLE imaging_study (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, study_no VARCHAR(48) NOT NULL, clinical_order_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, accession_no VARCHAR(64) NULL,
  modality VARCHAR(32) NOT NULL, body_part VARCHAR(128) NOT NULL, laterality VARCHAR(16) NULL, study_status VARCHAR(32) NOT NULL DEFAULT 'ORDERED', external_study_uid VARCHAR(128) NULL,
  performed_at DATETIME(3) NULL, provider VARCHAR(64) NOT NULL, version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_imaging_study_no (study_no), UNIQUE KEY uk_imaging_study_order (clinical_order_id), UNIQUE KEY uk_imaging_study_uid (external_study_uid), KEY idx_imaging_study_patient (patient_id,study_status),
  CONSTRAINT fk_imaging_study_order FOREIGN KEY (clinical_order_id) REFERENCES clinical_order(id)
);
CREATE TABLE critical_result_alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, result_item_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, encounter_id BIGINT NOT NULL, clinical_order_id BIGINT NOT NULL,
  severity VARCHAR(24) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'NEW', detected_at DATETIME(3) NOT NULL, source VARCHAR(64) NOT NULL,
  notified_at DATETIME(3) NULL, acknowledged_by BIGINT NULL, acknowledged_at DATETIME(3) NULL, acknowledge_note VARCHAR(512) NULL, escalated_at DATETIME(3) NULL, resolved_at DATETIME(3) NULL, trace_id VARCHAR(64) NOT NULL, version INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_critical_alert_item (result_item_id), KEY idx_critical_alert_doctor (clinical_order_id,status), KEY idx_critical_alert_patient (patient_id,status),
  CONSTRAINT fk_critical_alert_item FOREIGN KEY (result_item_id) REFERENCES lab_result_item(id), CONSTRAINT fk_critical_alert_order FOREIGN KEY (clinical_order_id) REFERENCES clinical_order(id)
);
CREATE TABLE clinical_result_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, report_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL, encounter_id BIGINT NOT NULL, reviewed_at DATETIME(3) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'REVIEWED', note VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_clinical_result_review (report_id,doctor_id), KEY idx_clinical_result_review_doctor (doctor_id,reviewed_at), CONSTRAINT fk_clinical_result_review_report FOREIGN KEY (report_id) REFERENCES diagnostic_report(id)
);
CREATE TABLE clinical_integration_anomaly (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, provider VARCHAR(64) NOT NULL, anomaly_type VARCHAR(64) NOT NULL, clinical_order_id BIGINT NULL, external_reference VARCHAR(128) NULL, status VARCHAR(32) NOT NULL DEFAULT 'MANUAL_REVIEW_REQUIRED', detail_safe VARCHAR(512) NULL, trace_id VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_clinical_anomaly_order (clinical_order_id,status)
);
CREATE TABLE clinical_diagnostic_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_id BIGINT NULL, action VARCHAR(64) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id BIGINT NOT NULL, trace_id VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_clinical_diagnostic_audit_resource (resource_type,resource_id,created_at)
);
INSERT INTO clinical_service_catalog(service_code,service_name,service_type,specimen_type,body_part_required,laterality_required,report_type,effective_from) VALUES
 ('TEST_LAB_PANEL','TEST LAB PANEL','LAB','WHOLE_BLOOD',0,0,'LAB',CURRENT_DATE),
 ('TEST_ECG','TEST ECG EXAMINATION','EXAMINATION',NULL,0,0,'EXAMINATION',CURRENT_DATE),
 ('TEST_CHEST_XR','TEST CHEST X-RAY','IMAGING',NULL,1,0,'IMAGING',CURRENT_DATE);
INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:order:create','临床医嘱开立','API'),('hospital:clinical:order:view','临床医嘱查看','API'),('hospital:clinical:order:cancel','临床医嘱取消','API'),('hospital:clinical:result:view','临床结果查看','API'),('hospital:clinical:result:review','临床结果确认已阅','API'),
 ('hospital:clinical:lab:workbench','检验工作台','API'),('hospital:clinical:lab:result:verify','检验结果审核','API'),('hospital:clinical:imaging:workbench','影像工作台','API'),('hospital:clinical:report:sign','诊断报告签署','API');
INSERT INTO sys_role(role_code,role_name,data_scope) VALUES
 ('CLINICAL_DOCTOR','临床医嘱医生','SELF'),('LAB_STAFF','检验工作人员','CUSTOM'),('RADIOLOGY_STAFF','影像报告工作人员','CUSTOM');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:order:create','hospital:clinical:order:view','hospital:clinical:order:cancel','hospital:clinical:result:view','hospital:clinical:result:review') WHERE r.role_code='CLINICAL_DOCTOR';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:lab:workbench','hospital:clinical:lab:result:verify') WHERE r.role_code='LAB_STAFF';
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('hospital:clinical:imaging:workbench','hospital:clinical:report:sign') WHERE r.role_code='RADIOLOGY_STAFF';
