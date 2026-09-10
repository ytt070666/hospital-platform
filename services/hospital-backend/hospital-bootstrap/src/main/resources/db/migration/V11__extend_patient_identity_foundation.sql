ALTER TABLE patient
  CHANGE COLUMN phone_ciphertext mobile_encrypted VARCHAR(512) NULL,
  CHANGE COLUMN identity_ciphertext id_number_encrypted VARCHAR(512) NULL,
  ADD COLUMN gender VARCHAR(16) NULL AFTER name_ciphertext,
  ADD COLUMN birth_date DATE NULL AFTER gender,
  ADD COLUMN mobile_hash CHAR(64) NULL AFTER mobile_encrypted,
  ADD COLUMN id_type VARCHAR(32) NULL AFTER mobile_hash,
  ADD COLUMN id_number_hash CHAR(64) NULL AFTER id_number_encrypted,
  ADD COLUMN real_name_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER id_number_hash,
  ADD COLUMN real_name_verified_at DATETIME(3) NULL AFTER real_name_status,
  ADD COLUMN default_member_id BIGINT NULL AFTER status,
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER default_member_id,
  ADD COLUMN encryption_key_version VARCHAR(16) NOT NULL DEFAULT 'v1' AFTER token_version,
  ADD UNIQUE KEY uk_patient_mobile_hash (mobile_hash),
  ADD KEY idx_patient_id_hash (id_number_hash),
  ADD KEY idx_patient_status_created (status, created_at),
  ADD KEY idx_patient_real_name_status (real_name_status);

ALTER TABLE patient_member
  ADD COLUMN member_no VARCHAR(64) NULL AFTER patient_id,
  ADD COLUMN name_encrypted VARCHAR(512) NULL AFTER relationship_code,
  ADD COLUMN gender VARCHAR(16) NULL AFTER name_encrypted,
  ADD COLUMN birth_date DATE NULL AFTER gender,
  ADD COLUMN id_type VARCHAR(32) NULL AFTER birth_date,
  ADD COLUMN id_number_encrypted VARCHAR(512) NULL AFTER id_type,
  ADD COLUMN id_number_hash CHAR(64) NULL AFTER id_number_encrypted,
  ADD COLUMN mobile_encrypted VARCHAR(512) NULL AFTER id_number_hash,
  ADD COLUMN mobile_hash CHAR(64) NULL AFTER mobile_encrypted,
  ADD COLUMN is_self TINYINT NOT NULL DEFAULT 0 AFTER mobile_hash,
  ADD COLUMN real_name_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER is_self,
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER real_name_status,
  ADD COLUMN hospital_card_no_encrypted VARCHAR(512) NULL AFTER status,
  ADD COLUMN health_card_reference_encrypted VARCHAR(512) NULL AFTER hospital_card_no_encrypted,
  ADD COLUMN guardian_member_id BIGINT NULL AFTER health_card_reference_encrypted,
  ADD COLUMN encryption_key_version VARCHAR(16) NOT NULL DEFAULT 'v1' AFTER guardian_member_id,
  ADD COLUMN self_patient_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=0 AND is_self=1 THEN patient_id ELSE NULL END) STORED,
  ADD COLUMN active_identity_hash CHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 AND status='ACTIVE' THEN id_number_hash ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_patient_member_no (member_no),
  ADD UNIQUE KEY uk_patient_member_self (self_patient_id),
  ADD UNIQUE KEY uk_patient_member_identity (active_identity_hash),
  ADD KEY idx_patient_member_patient_status (patient_id, status, deleted),
  ADD KEY idx_patient_member_mobile_hash (mobile_hash);

UPDATE patient_member
SET member_no = CONCAT('PM', LPAD(id, 12, '0'))
WHERE member_no IS NULL;
ALTER TABLE patient_member MODIFY member_no VARCHAR(64) NOT NULL;

CREATE TABLE patient_refresh_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  client_type VARCHAR(32) NOT NULL,
  audience VARCHAR(64) NOT NULL,
  token_version INT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  replaced_by_hash CHAR(64) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patient_refresh_token_hash (token_hash),
  KEY idx_patient_refresh_token_patient (patient_id),
  KEY idx_patient_refresh_token_expires (expires_at)
);

CREATE TABLE patient_hospital_card (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  hospital_id BIGINT NULL,
  campus_id BIGINT NULL,
  card_no_encrypted VARCHAR(512) NOT NULL,
  card_no_hash CHAR(64) NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  external_patient_id_encrypted VARCHAR(512) NULL,
  verified_at DATETIME(3) NULL,
  encryption_key_version VARCHAR(16) NOT NULL DEFAULT 'v1',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_patient_card_member (member_id, status, deleted),
  KEY idx_patient_card_hash (card_no_hash)
);

CREATE TABLE external_patient_reference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NULL,
  source_system VARCHAR(64) NOT NULL,
  external_patient_id_encrypted VARCHAR(512) NOT NULL,
  external_patient_id_hash CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  encryption_key_version VARCHAR(16) NOT NULL DEFAULT 'v1',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_external_patient_reference (source_system, external_patient_id_hash),
  KEY idx_external_patient_reference_patient (patient_id, status, deleted)
);
