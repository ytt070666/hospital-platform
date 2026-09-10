CREATE TABLE outpatient_medical_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_no VARCHAR(48) NOT NULL,
  encounter_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  current_revision_id BIGINT NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL,
  UNIQUE KEY uk_outpatient_record_no (record_no),
  UNIQUE KEY uk_outpatient_record_encounter (encounter_id),
  KEY idx_outpatient_record_patient (patient_id, member_id, deleted),
  CONSTRAINT fk_outpatient_record_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id)
);

CREATE TABLE outpatient_medical_record_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  medical_record_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  parent_revision_id BIGINT NULL,
  revision_type VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  chief_complaint TEXT NULL,
  present_illness MEDIUMTEXT NULL,
  past_history MEDIUMTEXT NULL,
  personal_history MEDIUMTEXT NULL,
  family_history MEDIUMTEXT NULL,
  physical_examination MEDIUMTEXT NULL,
  treatment_plan MEDIUMTEXT NULL,
  doctor_note MEDIUMTEXT NULL,
  amend_reason VARCHAR(512) NULL,
  content_hash CHAR(64) NULL,
  created_by BIGINT NOT NULL,
  signed_by BIGINT NULL,
  signed_at DATETIME(3) NULL,
  signature_type VARCHAR(48) NULL,
  external_signature_reference VARCHAR(256) NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_outpatient_record_revision_no (medical_record_id, revision_no),
  KEY idx_outpatient_revision_status (medical_record_id, status, signed_at),
  CONSTRAINT fk_outpatient_revision_record FOREIGN KEY (medical_record_id) REFERENCES outpatient_medical_record(id),
  CONSTRAINT fk_outpatient_revision_parent FOREIGN KEY (parent_revision_id) REFERENCES outpatient_medical_record_revision(id)
);

ALTER TABLE outpatient_medical_record
  ADD CONSTRAINT fk_outpatient_record_current_revision FOREIGN KEY (current_revision_id) REFERENCES outpatient_medical_record_revision(id);

CREATE TABLE clinical_vital_sign (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  encounter_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  measured_at DATETIME(3) NOT NULL,
  temperature_celsius DECIMAL(5,2) NULL,
  pulse_bpm SMALLINT NULL,
  respiratory_rate SMALLINT NULL,
  systolic_bp SMALLINT NULL,
  diastolic_bp SMALLINT NULL,
  spo2_percent DECIMAL(5,2) NULL,
  height_cm DECIMAL(6,2) NULL,
  weight_kg DECIMAL(6,2) NULL,
  bmi DECIMAL(6,2) NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  recorded_by BIGINT NOT NULL,
  corrected_from_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_clinical_vital_encounter_time (encounter_id, measured_at),
  KEY idx_clinical_vital_patient (patient_id, member_id, measured_at),
  CONSTRAINT fk_clinical_vital_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id),
  CONSTRAINT fk_clinical_vital_correction FOREIGN KEY (corrected_from_id) REFERENCES clinical_vital_sign(id)
);

CREATE TABLE patient_allergy_profile (
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  allergy_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  updated_by BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (patient_id, member_id),
  CONSTRAINT fk_allergy_profile_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
  CONSTRAINT fk_allergy_profile_member FOREIGN KEY (member_id) REFERENCES patient_member(id)
);

CREATE TABLE patient_allergy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  allergen_type VARCHAR(24) NOT NULL,
  allergen_code VARCHAR(64) NULL,
  allergen_name VARCHAR(256) NOT NULL,
  reaction VARCHAR(512) NULL,
  severity VARCHAR(24) NULL,
  clinical_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  verification_status VARCHAR(32) NOT NULL DEFAULT 'CLINICIAN_RECORDED',
  recorded_by BIGINT NOT NULL,
  verified_by BIGINT NULL,
  verified_at DATETIME(3) NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_patient_allergy_lookup (patient_id, member_id, clinical_status, deleted)
);

CREATE TABLE diagnosis_catalog (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code_system VARCHAR(64) NOT NULL,
  diagnosis_code VARCHAR(64) NOT NULL,
  diagnosis_name VARCHAR(256) NOT NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'HOSPITAL',
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_diagnosis_catalog_code (code_system, diagnosis_code),
  KEY idx_diagnosis_catalog_search (source, status, diagnosis_name)
);

CREATE TABLE encounter_diagnosis (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  encounter_id BIGINT NOT NULL,
  medical_record_revision_id BIGINT NOT NULL,
  diagnosis_type VARCHAR(24) NOT NULL,
  code_system VARCHAR(64) NOT NULL,
  diagnosis_code VARCHAR(64) NOT NULL,
  diagnosis_name_snapshot VARCHAR(256) NOT NULL,
  status VARCHAR(24) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  entered_by BIGINT NOT NULL,
  entered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  active_primary_revision_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 AND diagnosis_type = 'PRIMARY' AND status IN ('PROVISIONAL','CONFIRMED') THEN medical_record_revision_id ELSE NULL END) STORED,
  KEY idx_encounter_diagnosis_encounter (encounter_id, medical_record_revision_id),
  KEY idx_encounter_diagnosis_code (code_system, diagnosis_code),
  UNIQUE KEY uk_active_primary_diagnosis (active_primary_revision_id),
  CONSTRAINT fk_encounter_diagnosis_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id),
  CONSTRAINT fk_encounter_diagnosis_revision FOREIGN KEY (medical_record_revision_id) REFERENCES outpatient_medical_record_revision(id)
);

CREATE TABLE clinical_access_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  action VARCHAR(48) NOT NULL,
  resource_type VARCHAR(48) NOT NULL,
  resource_id BIGINT NOT NULL,
  revision_id BIGINT NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_clinical_audit_resource (resource_type, resource_id, created_at),
  KEY idx_clinical_audit_actor (actor_id, created_at)
);

INSERT INTO diagnosis_catalog(code_system, diagnosis_code, diagnosis_name, source) VALUES
  ('TEST','T001','测试性上呼吸道感染','TEST'),
  ('TEST','T002','测试性高血压','TEST'),
  ('TEST','T003','测试性发热','TEST');
