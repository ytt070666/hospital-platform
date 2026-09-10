CREATE TABLE sys_role_data_scope (
  role_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (role_id, target_type, target_id),
  KEY idx_sys_role_data_scope_target (target_type, target_id)
);
