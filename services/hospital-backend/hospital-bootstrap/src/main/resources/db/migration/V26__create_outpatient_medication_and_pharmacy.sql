CREATE TABLE drug_catalog (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  drug_code VARCHAR(64) NOT NULL,
  generic_name VARCHAR(256) NOT NULL,
  trade_name VARCHAR(256) NULL,
  dosage_form_code VARCHAR(64) NOT NULL,
  dosage_form_name VARCHAR(128) NOT NULL,
  strength_value DECIMAL(18,6) NOT NULL,
  strength_unit VARCHAR(32) NOT NULL,
  dose_unit VARCHAR(32) NOT NULL,
  package_unit VARCHAR(32) NOT NULL,
  dispensing_unit VARCHAR(32) NOT NULL,
  route_default VARCHAR(64) NULL,
  manufacturer VARCHAR(256) NULL,
  approval_reference VARCHAR(128) NULL,
  regulatory_class VARCHAR(32) NOT NULL DEFAULT 'TEST',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_drug_catalog_code (drug_code),
  KEY idx_drug_catalog_search (status, deleted, generic_name)
);

CREATE TABLE hospital_drug_formulary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  drug_id BIGINT NOT NULL,
  hospital_drug_code VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  prescribable TINYINT NOT NULL DEFAULT 1,
  dispensable TINYINT NOT NULL DEFAULT 1,
  price_snapshot_cent BIGINT NULL,
  pharmacy_category VARCHAR(32) NOT NULL DEFAULT 'OUTPATIENT',
  effective_from DATE NOT NULL,
  effective_to DATE NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_formulary_hospital_drug (hospital_id, drug_id),
  UNIQUE KEY uk_formulary_hospital_code (hospital_id, hospital_drug_code),
  KEY idx_formulary_search (hospital_id, enabled, prescribable),
  CONSTRAINT fk_formulary_drug FOREIGN KEY (drug_id) REFERENCES drug_catalog(id)
);

CREATE TABLE pharmacy_location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  campus_id BIGINT NULL,
  pharmacy_code VARCHAR(64) NOT NULL,
  pharmacy_name VARCHAR(128) NOT NULL,
  pharmacy_type VARCHAR(32) NOT NULL DEFAULT 'OUTPATIENT',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pharmacy_hospital_code (hospital_id, pharmacy_code),
  KEY idx_pharmacy_scope (hospital_id, campus_id, status)
);

CREATE TABLE pharmacy_user_scope (
  user_id BIGINT NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, pharmacy_id),
  CONSTRAINT fk_pharmacy_scope_location FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_location(id)
);

CREATE TABLE pharmacy_inventory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pharmacy_id BIGINT NOT NULL,
  drug_id BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inventory_pharmacy_drug (pharmacy_id, drug_id),
  CONSTRAINT fk_inventory_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_location(id),
  CONSTRAINT fk_inventory_drug FOREIGN KEY (drug_id) REFERENCES drug_catalog(id)
);

CREATE TABLE pharmacy_inventory_lot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inventory_id BIGINT NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  drug_id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  expiry_date DATE NOT NULL,
  on_hand_quantity DECIMAL(18,6) NOT NULL,
  reserved_quantity DECIMAL(18,6) NOT NULL DEFAULT 0,
  available_quantity DECIMAL(18,6) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inventory_lot_batch (pharmacy_id, drug_id, batch_no),
  KEY idx_lot_fefo (pharmacy_id, drug_id, status, expiry_date),
  CONSTRAINT ck_lot_on_hand_nonnegative CHECK (on_hand_quantity >= 0),
  CONSTRAINT ck_lot_reserved_nonnegative CHECK (reserved_quantity >= 0),
  CONSTRAINT ck_lot_available_nonnegative CHECK (available_quantity >= 0),
  CONSTRAINT ck_lot_balance CHECK (reserved_quantity + available_quantity <= on_hand_quantity),
  CONSTRAINT fk_lot_inventory FOREIGN KEY (inventory_id) REFERENCES pharmacy_inventory(id)
);

CREATE TABLE outpatient_prescription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_no VARCHAR(48) NOT NULL,
  encounter_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  medical_record_revision_id BIGINT NOT NULL,
  prescription_type VARCHAR(32) NOT NULL DEFAULT 'OUTPATIENT',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  signed_by BIGINT NULL,
  signed_at DATETIME(3) NULL,
  content_hash CHAR(64) NULL,
  supersedes_prescription_id BIGINT NULL,
  cancel_reason VARCHAR(512) NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_prescription_no (prescription_no),
  KEY idx_prescription_encounter (encounter_id, status),
  KEY idx_prescription_patient (patient_id, status),
  KEY idx_prescription_doctor (doctor_id, status),
  CONSTRAINT fk_prescription_encounter FOREIGN KEY (encounter_id) REFERENCES visit_encounter(id),
  CONSTRAINT fk_prescription_revision FOREIGN KEY (medical_record_revision_id) REFERENCES outpatient_medical_record_revision(id),
  CONSTRAINT fk_prescription_supersedes FOREIGN KEY (supersedes_prescription_id) REFERENCES outpatient_prescription(id)
);

CREATE TABLE prescription_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  drug_id BIGINT NOT NULL,
  drug_code_snapshot VARCHAR(64) NOT NULL,
  drug_name_snapshot VARCHAR(256) NOT NULL,
  dosage_form_snapshot VARCHAR(128) NOT NULL,
  strength_snapshot VARCHAR(128) NOT NULL,
  dose_amount DECIMAL(18,6) NOT NULL,
  dose_unit VARCHAR(32) NOT NULL,
  route_code VARCHAR(64) NOT NULL,
  route_name_snapshot VARCHAR(128) NOT NULL,
  frequency_code VARCHAR(64) NOT NULL,
  frequency_name_snapshot VARCHAR(128) NOT NULL,
  duration_value INT NOT NULL,
  duration_unit VARCHAR(32) NOT NULL,
  quantity DECIMAL(18,6) NOT NULL,
  quantity_unit VARCHAR(32) NOT NULL,
  usage_instruction VARCHAR(1024) NULL,
  sort_order INT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_prescription_item_drug (prescription_id, drug_id),
  KEY idx_prescription_item_prescription (prescription_id),
  KEY idx_prescription_item_drug (drug_id),
  CONSTRAINT fk_prescription_item_header FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id),
  CONSTRAINT fk_prescription_item_drug FOREIGN KEY (drug_id) REFERENCES drug_catalog(id)
);

CREATE TABLE prescription_safety_alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  severity VARCHAR(16) NOT NULL,
  rule_code VARCHAR(64) NOT NULL,
  display_message VARCHAR(512) NOT NULL,
  overridden_by BIGINT NULL,
  override_reason VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_prescription_safety (prescription_id, severity),
  CONSTRAINT fk_prescription_safety_header FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id)
);

CREATE TABLE pharmacy_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  pharmacist_id BIGINT NOT NULL,
  decision VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  reason VARCHAR(512) NULL,
  reviewed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_review_prescription (prescription_id, reviewed_at),
  CONSTRAINT fk_review_prescription FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id),
  CONSTRAINT fk_review_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_location(id)
);

CREATE TABLE pharmacy_stock_reservation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  prescription_item_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  quantity DECIMAL(18,6) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_reservation_item (prescription_item_id),
  KEY idx_reservation_prescription (prescription_id, status),
  CONSTRAINT fk_reservation_prescription FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id),
  CONSTRAINT fk_reservation_item FOREIGN KEY (prescription_item_id) REFERENCES prescription_item(id),
  CONSTRAINT fk_reservation_lot FOREIGN KEY (lot_id) REFERENCES pharmacy_inventory_lot(id)
);

CREATE TABLE dispensing_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  dispenser_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DISPENSING',
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dispensing_prescription (prescription_id),
  KEY idx_dispensing_pharmacy_status (pharmacy_id, status),
  CONSTRAINT fk_dispensing_prescription FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id),
  CONSTRAINT fk_dispensing_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_location(id)
);

CREATE TABLE dispensing_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dispensing_record_id BIGINT NOT NULL,
  prescription_item_id BIGINT NOT NULL,
  drug_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  quantity DECIMAL(18,6) NOT NULL,
  dispensed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  dispenser_id BIGINT NOT NULL,
  UNIQUE KEY uk_dispensing_item (dispensing_record_id, prescription_item_id),
  CONSTRAINT fk_dispensing_item_record FOREIGN KEY (dispensing_record_id) REFERENCES dispensing_record(id),
  CONSTRAINT fk_dispensing_item_lot FOREIGN KEY (lot_id) REFERENCES pharmacy_inventory_lot(id)
);

CREATE TABLE medication_return_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  return_no VARCHAR(48) NOT NULL,
  prescription_id BIGINT NOT NULL,
  dispensing_record_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  pharmacy_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
  reason_code VARCHAR(64) NOT NULL,
  reason VARCHAR(512) NULL,
  requested_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  restock_eligible TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_return_no (return_no),
  KEY idx_return_prescription (prescription_id, status),
  CONSTRAINT fk_return_prescription FOREIGN KEY (prescription_id) REFERENCES outpatient_prescription(id),
  CONSTRAINT fk_return_dispensing FOREIGN KEY (dispensing_record_id) REFERENCES dispensing_record(id)
);

CREATE TABLE medication_return_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  return_order_id BIGINT NOT NULL,
  dispensing_item_id BIGINT NOT NULL,
  quantity DECIMAL(18,6) NOT NULL,
  UNIQUE KEY uk_return_item (return_order_id, dispensing_item_id),
  CONSTRAINT fk_return_item_order FOREIGN KEY (return_order_id) REFERENCES medication_return_order(id),
  CONSTRAINT fk_return_item_dispensed FOREIGN KEY (dispensing_item_id) REFERENCES dispensing_item(id)
);

CREATE TABLE medication_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(48) NOT NULL,
  resource_id BIGINT NOT NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_medication_audit_resource (resource_type, resource_id, created_at)
);

INSERT INTO drug_catalog(drug_code,generic_name,dosage_form_code,dosage_form_name,strength_value,strength_unit,dose_unit,package_unit,dispensing_unit,route_default,regulatory_class,status) VALUES
 ('TEST_DRUG_001','测试片剂 A','TABLET','片剂',10,'mg','tablet','box','tablet','PO','TEST','ACTIVE'),
 ('TEST_DRUG_002','测试片剂 B（过敏阻断规则）','TABLET','片剂',20,'mg','tablet','box','tablet','PO','TEST','ACTIVE'),
 ('TEST_DRUG_003','测试口服液 C（警告规则）','SOLUTION','口服液',5,'mg','mL','bottle','mL','PO','TEST','ACTIVE');

INSERT INTO hospital_drug_formulary(hospital_id,drug_id,hospital_drug_code,effective_from)
 SELECT h.id,d.id,concat('TEST-',d.drug_code),CURRENT_DATE FROM hospital h JOIN drug_catalog d WHERE d.drug_code LIKE 'TEST_DRUG_%';
INSERT INTO pharmacy_location(hospital_id,campus_id,pharmacy_code,pharmacy_name)
 SELECT h.id,MIN(c.id),concat('TEST-OP-',h.id),concat('测试门诊药房-',h.id) FROM hospital h LEFT JOIN hospital_campus c ON c.hospital_id=h.id GROUP BY h.id;
INSERT INTO pharmacy_inventory(pharmacy_id,drug_id)
 SELECT p.id,d.id FROM pharmacy_location p JOIN drug_catalog d ON d.drug_code LIKE 'TEST_DRUG_%';
INSERT INTO pharmacy_inventory_lot(inventory_id,pharmacy_id,drug_id,batch_no,expiry_date,on_hand_quantity,available_quantity)
 SELECT i.id,i.pharmacy_id,i.drug_id,concat('TEST-LOT-',i.pharmacy_id,'-',i.drug_id),DATE_ADD(CURRENT_DATE,INTERVAL 365 DAY),100,100 FROM pharmacy_inventory i;

INSERT INTO sys_permission(permission_code,permission_name,permission_type) VALUES
 ('hospital:clinical:prescription:create','门诊处方创建','API'),
 ('hospital:clinical:prescription:view','门诊处方受控查看','API'),
 ('hospital:clinical:prescription:sign','门诊处方签署','API'),
 ('hospital:clinical:prescription:cancel','门诊处方取消','API'),
 ('hospital:medication:safety:override','用药安全警告覆盖','API'),
 ('hospital:pharmacy:review:list','药房待审处方查看','API'),
 ('hospital:pharmacy:review:approve','药房审方通过','API'),
 ('hospital:pharmacy:review:reject','药房审方拒绝','API'),
 ('hospital:pharmacy:dispense','药房调剂发药','API'),
 ('hospital:pharmacy:return','药房退药处理','API'),
 ('hospital:pharmacy:inventory:view','药房库存查看','API'),
 ('hospital:pharmacy:inventory:manage','药房库存调整','API'),
 ('hospital:drug:catalog:view','药品目录查看','API'),
 ('hospital:drug:catalog:manage','药品目录维护','API');
INSERT INTO sys_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='SUPER_ADMIN' AND p.permission_code IN
 ('hospital:clinical:prescription:create','hospital:clinical:prescription:view','hospital:clinical:prescription:sign','hospital:clinical:prescription:cancel','hospital:medication:safety:override','hospital:pharmacy:review:list','hospital:pharmacy:review:approve','hospital:pharmacy:review:reject','hospital:pharmacy:dispense','hospital:pharmacy:return','hospital:pharmacy:inventory:view','hospital:pharmacy:inventory:manage','hospital:drug:catalog:view','hospital:drug:catalog:manage');
INSERT INTO sys_menu(permission_id,menu_code,menu_name,route_path,component,sort_order)
 SELECT id,'pharmacy-workbench','药房工作台','/pharmacy-workbench','PharmacyWorkbench',55 FROM sys_permission WHERE permission_code='hospital:pharmacy:review:list';
