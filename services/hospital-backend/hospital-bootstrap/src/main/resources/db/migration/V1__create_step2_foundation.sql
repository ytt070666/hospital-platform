CREATE TABLE sys_organization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NULL, org_code VARCHAR(64) NOT NULL, org_name VARCHAR(128) NOT NULL, org_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_organization_code (org_code), KEY idx_sys_organization_parent (parent_id), KEY idx_sys_organization_created_at (created_at)
);
CREATE TABLE sys_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, organization_id BIGINT NOT NULL, parent_id BIGINT NULL, dept_code VARCHAR(64) NOT NULL, dept_name VARCHAR(128) NOT NULL, dept_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_department_org_code (organization_id, dept_code), KEY idx_sys_department_parent (parent_id), KEY idx_sys_department_org (organization_id)
);
CREATE TABLE department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, organization_id BIGINT NOT NULL, administrative_department_id BIGINT NULL, department_code VARCHAR(64) NOT NULL, department_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_department_org_code (organization_id, department_code), KEY idx_department_org (organization_id)
);
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(128) NOT NULL, phone_ciphertext VARCHAR(512) NULL,
  status TINYINT NOT NULL DEFAULT 1, token_version INT NOT NULL DEFAULT 0, login_fail_count INT NOT NULL DEFAULT 0, locked_until DATETIME(3) NULL, last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_user_username (username), KEY idx_sys_user_created_at (created_at)
);
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, role_code VARCHAR(64) NOT NULL, role_name VARCHAR(128) NOT NULL, data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF', custom_scope_json JSON NULL,
  status TINYINT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_role_code (role_code)
);
CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, permission_code VARCHAR(128) NOT NULL, permission_name VARCHAR(128) NOT NULL, permission_type VARCHAR(32) NOT NULL DEFAULT 'API', resource_path VARCHAR(255) NULL, http_method VARCHAR(16) NULL,
  status TINYINT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_permission_code (permission_code)
);
CREATE TABLE sys_user_role (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (user_id, role_id), KEY idx_sys_user_role_role (role_id));
CREATE TABLE sys_role_permission (role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (role_id, permission_id), KEY idx_sys_role_permission_permission (permission_id));
CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NULL, permission_id BIGINT NULL, menu_code VARCHAR(64) NOT NULL, menu_name VARCHAR(128) NOT NULL, route_path VARCHAR(255) NOT NULL, component VARCHAR(255) NULL, icon VARCHAR(64) NULL, sort_order INT NOT NULL DEFAULT 0,
  visible TINYINT NOT NULL DEFAULT 1, status TINYINT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_menu_code (menu_code), KEY idx_sys_menu_parent (parent_id), KEY idx_sys_menu_permission (permission_id)
);
CREATE TABLE sys_user_department (user_id BIGINT NOT NULL, department_id BIGINT NOT NULL, is_primary TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (user_id, department_id), KEY idx_sys_user_department_department (department_id));
CREATE TABLE sys_refresh_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL, client_type VARCHAR(32) NOT NULL, audience VARCHAR(64) NOT NULL, token_version INT NOT NULL, expires_at DATETIME(3) NOT NULL, revoked_at DATETIME(3) NULL, replaced_by_hash CHAR(64) NULL, ip_address VARCHAR(64) NULL, user_agent VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_refresh_token_hash (token_hash), KEY idx_sys_refresh_token_user (user_id), KEY idx_sys_refresh_token_expires (expires_at)
);
CREATE TABLE sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NULL, username VARCHAR(64) NULL, success TINYINT NOT NULL, client_type VARCHAR(32) NULL, ip_address VARCHAR(64) NULL, failure_reason VARCHAR(128) NULL, trace_id VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sys_login_log_user_created (user_id, created_at), KEY idx_sys_login_log_trace (trace_id)
);
CREATE TABLE sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NULL, action VARCHAR(128) NOT NULL, resource_type VARCHAR(64) NULL, resource_id VARCHAR(64) NULL, result VARCHAR(16) NOT NULL, ip_address VARCHAR(64) NULL, trace_id VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sys_operation_log_user_created (user_id, created_at), KEY idx_sys_operation_log_trace (trace_id)
);
CREATE TABLE sys_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NULL, account_name VARCHAR(64) NULL, action VARCHAR(128) NOT NULL, resource_type VARCHAR(64) NULL, resource_id VARCHAR(64) NULL, purpose VARCHAR(256) NULL, result VARCHAR(16) NOT NULL, ip_address VARCHAR(64) NULL, request_path VARCHAR(255) NULL, trace_id VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sys_audit_log_user_created (user_id, created_at), KEY idx_sys_audit_log_trace (trace_id)
);
CREATE TABLE sys_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, config_key VARCHAR(128) NOT NULL, config_value_ciphertext TEXT NULL, value_type VARCHAR(32) NOT NULL DEFAULT 'STRING', secret_flag TINYINT NOT NULL DEFAULT 0, description VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_config_key (config_key)
);
CREATE TABLE sys_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, object_key VARCHAR(512) NOT NULL, original_name VARCHAR(255) NOT NULL, content_type VARCHAR(128) NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, bucket_name VARCHAR(128) NOT NULL, classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL', uploader_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sys_file_object_key (object_key), KEY idx_sys_file_uploader_created (uploader_id, created_at)
);
CREATE TABLE patient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_no VARCHAR(64) NOT NULL, name_ciphertext VARCHAR(512) NOT NULL, phone_ciphertext VARCHAR(512) NULL, identity_ciphertext VARCHAR(512) NULL, status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_patient_no (patient_no)
);
CREATE TABLE patient_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, patient_id BIGINT NOT NULL, member_patient_id BIGINT NULL, relationship_code VARCHAR(32) NOT NULL, authorization_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  KEY idx_patient_member_patient (patient_id)
);
CREATE TABLE doctor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_no VARCHAR(64) NOT NULL, department_id BIGINT NULL, name_ciphertext VARCHAR(512) NOT NULL, title_name VARCHAR(64) NULL, status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), created_by BIGINT NULL, updated_by BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_doctor_no (doctor_no), KEY idx_doctor_department (department_id)
);
