package com.hospital.platform.clinical;

/** Boundary for a future hospital/CA signature integration. This implementation is an authenticated internal signature, not a CA signature. */
public interface ClinicalSignatureProvider {
  String type();
  default String externalReference() { return null; }
}
