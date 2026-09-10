package com.hospital.platform.common.security;

import java.security.Principal;

/** Patient identity is intentionally separate from staff sys_user identity. */
public record AuthenticatedPatient(Long patientId, int tokenVersion, String clientType) implements Principal {
  @Override public String getName() { return "patient:" + patientId; }
}
