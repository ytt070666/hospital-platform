package com.hospital.platform.medication;

/**
 * Boundary for a future medication-charge integration. STEP 7.3 deliberately does not initiate
 * external charging: stock reservation and dispensing remain local, auditable transactions.
 */
public interface MedicationBillingGateway {
  ChargeStatus queryChargeStatus(long prescriptionId);
  enum ChargeStatus { NOT_REQUIRED, PENDING, PAID, REFUNDED, UNKNOWN }
}
