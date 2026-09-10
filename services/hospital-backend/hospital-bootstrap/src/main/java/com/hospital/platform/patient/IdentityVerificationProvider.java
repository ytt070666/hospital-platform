package com.hospital.platform.patient;

/** Adapter boundary for future verified health-card, HIS MPI, or real-name vendors. */
public interface IdentityVerificationProvider {
  VerificationResult verify(VerificationRequest request);
  record VerificationRequest(String idType, String normalizedIdNumber, String name) { }
  record VerificationResult(String status, String providerReference) { }
}
