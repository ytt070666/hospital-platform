ALTER TABLE hospital ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER business_hours;

CREATE TABLE schedule_session_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_session_definition_hospital_code (hospital_id, code),
  KEY idx_session_definition_hospital (hospital_id, status, deleted, sort_order)
);

CREATE TABLE schedule_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  doctor_id BIGINT NOT NULL,
  campus_id BIGINT NOT NULL,
  outpatient_department_id BIGINT NOT NULL,
  clinic_type_id BIGINT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE NOT NULL,
  quota_per_session INT NOT NULL,
  slot_duration_minutes INT NULL,
  slot_quota INT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  published TINYINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL,
  UNIQUE KEY uk_schedule_template_code (template_code),
  KEY idx_schedule_template_doctor (doctor_id, status, deleted),
  KEY idx_schedule_template_outpatient (outpatient_department_id, status, deleted)
);

CREATE TABLE schedule_template_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL,
  session_definition_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_schedule_template_rule (template_id, day_of_week, session_definition_id),
  KEY idx_schedule_template_rule_template (template_id, status, deleted)
);

CREATE TABLE doctor_schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_no VARCHAR(64) NOT NULL,
  doctor_id BIGINT NOT NULL,
  original_doctor_id BIGINT NULL,
  substitute_doctor_id BIGINT NULL,
  campus_id BIGINT NOT NULL,
  outpatient_department_id BIGINT NOT NULL,
  clinic_type_id BIGINT NULL,
  clinic_type_unique_id BIGINT GENERATED ALWAYS AS (COALESCE(clinic_type_id, 0)) STORED,
  schedule_date DATE NOT NULL,
  session_definition_id BIGINT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  schedule_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  source VARCHAR(24) NOT NULL,
  template_id BIGINT NULL,
  total_quota INT NOT NULL,
  reserved_quota INT NOT NULL DEFAULT 0,
  booked_quota INT NOT NULL DEFAULT 0,
  slot_mode TINYINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL,
  UNIQUE KEY uk_doctor_schedule_business (doctor_id, schedule_date, session_definition_id, campus_id, outpatient_department_id, clinic_type_unique_id),
  KEY idx_schedule_doctor_date (doctor_id, schedule_date, schedule_status, deleted),
  KEY idx_schedule_outpatient_date (outpatient_department_id, schedule_date, schedule_status, deleted),
  KEY idx_schedule_public (schedule_status, schedule_date, campus_id, clinic_type_id, deleted),
  KEY idx_schedule_template (template_id, schedule_date)
);

CREATE TABLE schedule_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  slot_no VARCHAR(32) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  total_quota INT NOT NULL,
  reserved_quota INT NOT NULL DEFAULT 0,
  booked_quota INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_schedule_slot_no (schedule_id, slot_no),
  KEY idx_schedule_slot_schedule (schedule_id, status, deleted, start_time)
);

CREATE TABLE schedule_change_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_summary VARCHAR(1024) NULL,
  after_summary VARCHAR(1024) NULL,
  reason_code VARCHAR(64) NULL,
  reason VARCHAR(512) NULL,
  operator_id BIGINT NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_schedule_change_schedule_created (schedule_id, created_at),
  KEY idx_schedule_change_trace (trace_id)
);
