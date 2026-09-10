ALTER TABLE registration_order
  ADD COLUMN refunded_amount_cent BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN refund_reserved_amount_cent BIGINT NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_registration_refund_amount CHECK (refunded_amount_cent >= 0 AND refund_reserved_amount_cent >= 0 AND refunded_amount_cent + refund_reserved_amount_cent <= amount_cent);

CREATE TABLE refund_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 refund_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 refund_business_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 registration_order_id BIGINT NOT NULL, payment_order_id BIGINT NOT NULL,
 appointment_id BIGINT NOT NULL, patient_id BIGINT NOT NULL,
 provider VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_transaction_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_refund_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 refund_amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL,
 reason_code VARCHAR(32) NOT NULL, reason VARCHAR(512) NULL,
 client_request_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
 requested_at DATETIME(6) NULL, success_at DATETIME(6) NULL, failed_at DATETIME(6) NULL, closed_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_refund_registration FOREIGN KEY(registration_order_id) REFERENCES registration_order(id),
 CONSTRAINT fk_refund_payment FOREIGN KEY(payment_order_id) REFERENCES payment_order(id),
 CONSTRAINT fk_refund_appointment FOREIGN KEY(appointment_id) REFERENCES appointment(id),
 CONSTRAINT fk_refund_patient FOREIGN KEY(patient_id) REFERENCES patient(id),
 CONSTRAINT ck_refund_amount CHECK(refund_amount_cent BETWEEN 1 AND 1000000000000 AND currency='CNY'),
 CONSTRAINT ck_refund_status CHECK(status IN ('CREATED','PENDING','SUCCESS','FAILED','CLOSED')),
 UNIQUE KEY uk_refund_provider_no(provider,provider_refund_no),
 UNIQUE KEY uk_refund_client(patient_id,client_request_id),
 INDEX ix_refund_registration(registration_order_id,status,id), INDEX ix_refund_payment(payment_order_id,status,id),
 INDEX ix_refund_patient(patient_id,created_at), INDEX ix_refund_appointment(appointment_id,status), INDEX ix_refund_status(status,updated_at,id)
);

CREATE TABLE refund_callback_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 provider VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 nonce VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 refund_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_refund_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
 signature_verified BOOLEAN NOT NULL, processing_status VARCHAR(24) NOT NULL,
 result_code VARCHAR(64) NULL, payload_hash CHAR(64) NOT NULL, trace_id VARCHAR(64) NOT NULL,
 received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), processed_at DATETIME(6) NULL,
 UNIQUE KEY uk_refund_callback_event(provider,event_id), UNIQUE KEY uk_refund_callback_nonce(provider,nonce),
 INDEX ix_refund_callback_order(refund_no,received_at)
);

CREATE TABLE payment_reconciliation_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 anomaly_type VARCHAR(64) NOT NULL, resource_type VARCHAR(32) NOT NULL, resource_id BIGINT NOT NULL,
 severity VARCHAR(16) NOT NULL, local_state VARCHAR(128) NOT NULL, provider_state VARCHAR(128) NULL,
 resolution VARCHAR(64) NOT NULL DEFAULT 'MANUAL_REVIEW_REQUIRED', status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 trace_id VARCHAR(64) NOT NULL, detected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), resolved_at DATETIME(6) NULL,
 UNIQUE KEY uk_reconciliation_open(anomaly_type,resource_type,resource_id,status),
 INDEX ix_reconciliation_status(status,detected_at,id)
);

CREATE TABLE test_refund_provider_order (
 refund_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 amount_cent BIGINT NOT NULL, currency CHAR(3) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 provider_refund_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_test_refund_status CHECK(status IN ('PENDING','SUCCESS','FAILED','CLOSED'))
);
