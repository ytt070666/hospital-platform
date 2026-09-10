package com.hospital.platform.medication;

import java.util.List;

/** Adapter boundary for medication knowledge. Production knowledge bases are deliberately external. */
public interface MedicationSafetyProvider {
  List<Alert> check(long patientId, long memberId, List<String> drugCodes);
  record Alert(String severity, String ruleCode, String message) {}
}
