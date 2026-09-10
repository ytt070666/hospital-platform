CREATE TABLE pharmacy_inventory_adjustment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pharmacy_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  delta_quantity DECIMAL(18,6) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  reason VARCHAR(512) NULL,
  adjusted_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_inventory_adjustment_lot (lot_id, created_at),
  KEY idx_inventory_adjustment_pharmacy (pharmacy_id, created_at),
  CONSTRAINT fk_inventory_adjustment_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_location(id),
  CONSTRAINT fk_inventory_adjustment_lot FOREIGN KEY (lot_id) REFERENCES pharmacy_inventory_lot(id)
);
