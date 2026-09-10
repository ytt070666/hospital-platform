CREATE TABLE hospital (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  short_name VARCHAR(64) NULL,
  logo_file_id BIGINT NULL,
  hospital_level VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  hospital_type VARCHAR(64) NULL,
  introduction TEXT NULL,
  telephone VARCHAR(32) NULL,
  service_hotline VARCHAR(32) NULL,
  official_website VARCHAR(255) NULL,
  address VARCHAR(255) NULL,
  longitude DECIMAL(10,7) NULL,
  latitude DECIMAL(10,7) NULL,
  postal_code VARCHAR(16) NULL,
  business_hours VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_hospital_code (hospital_code),
  KEY idx_hospital_public (status, published, deleted, sort_order)
);

CREATE TABLE hospital_campus (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  campus_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  short_name VARCHAR(64) NULL,
  address VARCHAR(255) NULL,
  telephone VARCHAR(32) NULL,
  longitude DECIMAL(10,7) NULL, latitude DECIMAL(10,7) NULL,
  transportation TEXT NULL, parking_info TEXT NULL, business_hours VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_hospital_campus_code (hospital_id, campus_code),
  KEY idx_campus_public (hospital_id, status, published, deleted, sort_order)
);

CREATE TABLE hospital_building (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  campus_id BIGINT NOT NULL, building_code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  description TEXT NULL, map_reference VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_campus_building_code (campus_id, building_code), KEY idx_building_campus (campus_id, status, published, deleted, sort_order)
);

CREATE TABLE hospital_floor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  building_id BIGINT NOT NULL, floor_code VARCHAR(32) NOT NULL, floor_name VARCHAR(64) NOT NULL, floor_number INT NOT NULL,
  description TEXT NULL, map_reference VARCHAR(255) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_building_floor_code (building_id, floor_code), KEY idx_floor_building (building_id, status, published, deleted, sort_order)
);

ALTER TABLE department
  ADD COLUMN hospital_id BIGINT NULL AFTER organization_id,
  ADD COLUMN campus_id BIGINT NULL AFTER hospital_id,
  ADD COLUMN parent_id BIGINT NULL AFTER administrative_department_id,
  ADD COLUMN short_name VARCHAR(64) NULL AFTER department_name,
  ADD COLUMN category VARCHAR(64) NULL AFTER short_name,
  ADD COLUMN introduction TEXT NULL AFTER category,
  ADD COLUMN specialty_description TEXT NULL AFTER introduction,
  ADD COLUMN telephone VARCHAR(32) NULL AFTER specialty_description,
  ADD COLUMN location_description VARCHAR(255) NULL AFTER telephone,
  ADD COLUMN building_id BIGINT NULL AFTER location_description,
  ADD COLUMN floor_id BIGINT NULL AFTER building_id,
  ADD COLUMN logo_file_id BIGINT NULL AFTER floor_id,
  ADD COLUMN published TINYINT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER published,
  ADD KEY idx_department_parent (parent_id),
  ADD KEY idx_department_public (hospital_id, campus_id, status, published, deleted, sort_order);

CREATE TABLE clinic_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, description VARCHAR(512) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_clinic_type_code (code), KEY idx_clinic_type_public (status, published, deleted, sort_order)
);

CREATE TABLE outpatient_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, department_id BIGINT NOT NULL, clinic_type_id BIGINT NULL, outpatient_code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL, status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_outpatient_department_code (outpatient_code), KEY idx_outpatient_department (department_id, status, published, deleted, sort_order)
);

CREATE TABLE doctor_title (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, title_level INT NOT NULL DEFAULT 0, description VARCHAR(512) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_doctor_title_code (code), KEY idx_doctor_title_public (status, published, deleted, sort_order)
);

CREATE TABLE medical_specialty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, description VARCHAR(512) NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, published TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_medical_specialty_code (code), KEY idx_specialty_public (status, published, deleted, sort_order)
);

ALTER TABLE doctor
  ADD COLUMN user_id BIGINT NULL AFTER doctor_no,
  ADD COLUMN name VARCHAR(128) NULL AFTER name_ciphertext,
  ADD COLUMN gender VARCHAR(16) NULL AFTER name,
  ADD COLUMN avatar_file_id BIGINT NULL AFTER gender,
  ADD COLUMN title_id BIGINT NULL AFTER avatar_file_id,
  ADD COLUMN profile TEXT NULL AFTER title_id,
  ADD COLUMN education VARCHAR(512) NULL AFTER profile,
  ADD COLUMN medical_experience TEXT NULL AFTER education,
  ADD COLUMN specialty_summary VARCHAR(1024) NULL AFTER medical_experience,
  ADD COLUMN consultation_notice VARCHAR(1024) NULL AFTER specialty_summary,
  ADD COLUMN published TINYINT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER published,
  ADD KEY idx_doctor_public (status, published, deleted, sort_order),
  ADD KEY idx_doctor_title (title_id);
UPDATE doctor SET name=name_ciphertext WHERE name IS NULL;

CREATE TABLE doctor_specialty (
  doctor_id BIGINT NOT NULL, specialty_id BIGINT NOT NULL, sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (doctor_id, specialty_id), KEY idx_doctor_specialty_specialty (specialty_id, doctor_id)
);
CREATE TABLE doctor_department (
  doctor_id BIGINT NOT NULL, department_id BIGINT NOT NULL, relation_type VARCHAR(32) NOT NULL DEFAULT 'MEMBER', is_primary TINYINT NOT NULL DEFAULT 0,
  primary_doctor_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_primary=1 THEN doctor_id ELSE NULL END) STORED,
  sort_order INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (doctor_id, department_id), UNIQUE KEY uk_doctor_primary_department (primary_doctor_id), KEY idx_doctor_department_department (department_id, status, sort_order)
);
CREATE TABLE doctor_campus (
  doctor_id BIGINT NOT NULL, campus_id BIGINT NOT NULL, status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (doctor_id, campus_id), KEY idx_doctor_campus_campus (campus_id, status)
);
CREATE TABLE doctor_practice_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_id BIGINT NOT NULL, practice_type VARCHAR(64) NULL, practice_scope VARCHAR(512) NULL,
  practice_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', certificate_reference VARCHAR(255) NULL, valid_from DATE NULL, valid_to DATE NULL,
  verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', internal_remark VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_doctor_practice_info_doctor (doctor_id)
);
