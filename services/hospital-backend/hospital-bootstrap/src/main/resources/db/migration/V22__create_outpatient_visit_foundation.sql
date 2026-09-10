CREATE TABLE clinic_room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  campus_id BIGINT NOT NULL,
  building_id BIGINT NOT NULL,
  floor_id BIGINT NOT NULL,
  outpatient_department_id BIGINT NOT NULL,
  room_code VARCHAR(32) NOT NULL,
  room_name VARCHAR(64) NOT NULL,
  room_number VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL,
  UNIQUE KEY uk_clinic_room_campus_code (campus_id, room_code),
  KEY idx_clinic_room_outpatient (outpatient_department_id, status, deleted, sort_order)
);

ALTER TABLE doctor_schedule ADD COLUMN clinic_room_id BIGINT NULL AFTER clinic_type_id,
  ADD KEY idx_schedule_room_date (clinic_room_id, schedule_date, schedule_status, deleted);

CREATE TABLE visit_encounter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  encounter_no VARCHAR(48) NOT NULL,
  appointment_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  outpatient_department_id BIGINT NOT NULL,
  campus_id BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL,
  slot_id BIGINT NULL,
  clinic_room_id BIGINT NULL,
  encounter_date DATE NOT NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'APPOINTMENT',
  status VARCHAR(24) NOT NULL,
  checked_in_at DATETIME(3) NULL,
  waiting_at DATETIME(3) NULL,
  called_at DATETIME(3) NULL,
  started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by BIGINT NULL, updated_by BIGINT NULL,
  UNIQUE KEY uk_visit_encounter_no (encounter_no),
  UNIQUE KEY uk_visit_encounter_appointment (appointment_id),
  KEY idx_visit_encounter_doctor_date (doctor_id, encounter_date, status, deleted),
  KEY idx_visit_encounter_patient (patient_id, encounter_date, deleted),
  KEY idx_visit_encounter_schedule (schedule_id, encounter_date, status)
);

CREATE TABLE visit_encounter_status_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  encounter_id BIGINT NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  reason_code VARCHAR(64) NULL,
  reason VARCHAR(512) NULL,
  operator_type VARCHAR(24) NOT NULL,
  operator_id BIGINT NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_visit_history_encounter (encounter_id, id),
  KEY idx_visit_history_trace (trace_id)
);

CREATE TABLE queue_sequence_counter (
  schedule_id BIGINT NOT NULL,
  queue_date DATE NOT NULL,
  next_sequence INT NOT NULL,
  PRIMARY KEY (schedule_id, queue_date)
);

CREATE TABLE queue_ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  queue_no VARCHAR(32) NOT NULL,
  encounter_id BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  outpatient_department_id BIGINT NOT NULL,
  clinic_room_id BIGINT NULL,
  queue_date DATE NOT NULL,
  status VARCHAR(24) NOT NULL,
  queue_sequence INT NOT NULL,
  priority_code VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  checkin_at DATETIME(3) NOT NULL,
  called_at DATETIME(3) NULL,
  service_started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  skipped_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_queue_ticket_encounter (encounter_id),
  UNIQUE KEY uk_queue_schedule_date_sequence (schedule_id, queue_date, queue_sequence),
  UNIQUE KEY uk_queue_schedule_date_no (schedule_id, queue_date, queue_no),
  KEY idx_queue_next (schedule_id, queue_date, status, priority_code, checkin_at, queue_sequence),
  KEY idx_queue_doctor_date (doctor_id, queue_date, status)
);

ALTER TABLE visit_encounter ADD CONSTRAINT fk_visit_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id),
  ADD CONSTRAINT fk_visit_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
  ADD CONSTRAINT fk_visit_member FOREIGN KEY (member_id) REFERENCES patient_member(id),
  ADD CONSTRAINT fk_visit_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id);
ALTER TABLE visit_encounter_status_history ADD CONSTRAINT fk_visit_history_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id);
ALTER TABLE queue_ticket ADD CONSTRAINT fk_queue_ticket_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id);
