ALTER TABLE patient_member ADD COLUMN name_hash CHAR(64) NULL AFTER name_encrypted;
CREATE INDEX idx_patient_member_name_hash ON patient_member(name_hash);
