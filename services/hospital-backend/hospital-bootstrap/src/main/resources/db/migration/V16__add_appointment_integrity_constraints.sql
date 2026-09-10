ALTER TABLE appointment
  ADD CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
  ADD CONSTRAINT fk_appointment_member FOREIGN KEY (member_id) REFERENCES patient_member(id),
  ADD CONSTRAINT fk_appointment_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id),
  ADD CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id) REFERENCES schedule_slot(id);

ALTER TABLE appointment_status_history
  ADD CONSTRAINT fk_appointment_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id);
