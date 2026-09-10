ALTER TABLE clinic_type ADD COLUMN payment_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE registration_fee_rule (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 rule_code VARCHAR(64) NOT NULL UNIQUE,
 hospital_id BIGINT NOT NULL, campus_id BIGINT NULL, department_id BIGINT NULL,
 clinic_type_id BIGINT NULL, doctor_title_id BIGINT NULL,
 amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'CNY',
 effective_from DATETIME(6) NOT NULL, effective_to DATETIME(6) NULL,
 status TINYINT NOT NULL DEFAULT 1, priority INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_fee_hospital FOREIGN KEY(hospital_id) REFERENCES hospital(id),
 CONSTRAINT fk_fee_campus FOREIGN KEY(campus_id) REFERENCES hospital_campus(id),
 CONSTRAINT fk_fee_department FOREIGN KEY(department_id) REFERENCES department(id),
 CONSTRAINT fk_fee_clinic FOREIGN KEY(clinic_type_id) REFERENCES clinic_type(id),
 CONSTRAINT fk_fee_title FOREIGN KEY(doctor_title_id) REFERENCES doctor_title(id),
 CONSTRAINT ck_fee_money CHECK(amount_cent BETWEEN 0 AND 1000000000000 AND currency='CNY'),
 CONSTRAINT ck_fee_dates CHECK(effective_to IS NULL OR effective_to>effective_from),
 CONSTRAINT ck_fee_status CHECK(status IN (0,1)),
 INDEX ix_fee_match(hospital_id,status,deleted,effective_from,effective_to)
);
CREATE TABLE registration_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 appointment_id BIGINT NOT NULL UNIQUE, patient_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL,
 status VARCHAR(24) NOT NULL, pricing_rule_id BIGINT NULL, pricing_snapshot JSON NOT NULL,
 payment_deadline DATETIME(6) NOT NULL, paid_at DATETIME(6) NULL, closed_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_reg_appointment FOREIGN KEY(appointment_id) REFERENCES appointment(id),
 CONSTRAINT fk_reg_patient FOREIGN KEY(patient_id) REFERENCES patient(id),
 CONSTRAINT fk_reg_member FOREIGN KEY(member_id) REFERENCES patient_member(id),
 CONSTRAINT fk_reg_fee FOREIGN KEY(pricing_rule_id) REFERENCES registration_fee_rule(id),
 CONSTRAINT ck_reg_money CHECK(amount_cent BETWEEN 0 AND 1000000000000 AND currency='CNY'),
 CONSTRAINT ck_reg_status CHECK(status IN ('PENDING_PAYMENT','PAID','CLOSED')),
 INDEX ix_reg_patient(patient_id,created_at), INDEX ix_reg_deadline(status,payment_deadline,id)
);
CREATE TABLE payment_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 registration_order_id BIGINT NOT NULL, patient_id BIGINT NOT NULL,
 provider VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, channel VARCHAR(24) NOT NULL,
 amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL, status VARCHAR(16) NOT NULL,
 provider_order_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 provider_transaction_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 client_request_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_fingerprint CHAR(64) NOT NULL,
 expires_at DATETIME(6) NOT NULL, success_at DATETIME(6) NULL, failed_at DATETIME(6) NULL, closed_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 active_registration_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('CREATED','PENDING') THEN registration_order_id ELSE NULL END) STORED,
 successful_registration_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUCCESS' THEN registration_order_id ELSE NULL END) STORED,
 CONSTRAINT fk_payment_reg FOREIGN KEY(registration_order_id) REFERENCES registration_order(id),
 CONSTRAINT ck_payment_money CHECK(amount_cent BETWEEN 1 AND 1000000000000 AND currency='CNY'),
 CONSTRAINT ck_payment_status CHECK(status IN ('CREATED','PENDING','SUCCESS','FAILED','CLOSED')),
 CONSTRAINT ck_payment_success_transaction CHECK(status<>'SUCCESS' OR provider_transaction_id IS NOT NULL),
 UNIQUE KEY uk_payment_request(patient_id,client_request_id),
 UNIQUE KEY uk_payment_transaction(provider,provider_transaction_id),
 UNIQUE KEY uk_payment_active(active_registration_id),
 UNIQUE KEY uk_payment_success(successful_registration_id),
 INDEX ix_payment_registration(registration_order_id,created_at),
 INDEX ix_payment_patient(patient_id,created_at), INDEX ix_payment_expiry(status,expires_at,id)
);
CREATE TABLE payment_callback_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, provider VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 nonce VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_transaction_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 signature_verified BOOLEAN NOT NULL,
 processing_status VARCHAR(24) NOT NULL, result_code VARCHAR(64) NULL,
 received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), processed_at DATETIME(6) NULL,
 payload_hash CHAR(64) NOT NULL, trace_id VARCHAR(64) NOT NULL,
 UNIQUE KEY uk_callback_event(provider,event_id), UNIQUE KEY uk_callback_nonce(provider,nonce),
 INDEX ix_callback_payment(payment_no,received_at)
);
CREATE TABLE payment_transaction (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, payment_order_id BIGINT NOT NULL,
 operation VARCHAR(32) NOT NULL, provider_status VARCHAR(24) NOT NULL,
 result_code VARCHAR(64) NOT NULL, trace_id VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_payment_tx FOREIGN KEY(payment_order_id) REFERENCES payment_order(id),
 INDEX ix_payment_tx(payment_order_id,created_at)
);
CREATE TABLE payment_audit_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, action VARCHAR(64) NOT NULL,
 resource_type VARCHAR(32) NOT NULL, resource_id BIGINT NULL,
 actor_type VARCHAR(16) NOT NULL, actor_id BIGINT NULL, result_code VARCHAR(64) NOT NULL,
 before_snapshot JSON NULL, after_snapshot JSON NULL, trace_id VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_payment_audit(resource_type,resource_id,created_at)
);
-- Durable simulated provider truth. No real bank/merchant credentials or payloads.
CREATE TABLE test_payment_provider_order (
 payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 transaction_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
