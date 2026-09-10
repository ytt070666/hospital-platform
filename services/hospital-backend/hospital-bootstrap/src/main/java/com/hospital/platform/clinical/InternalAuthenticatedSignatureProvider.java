package com.hospital.platform.clinical;

import org.springframework.stereotype.Component;

@Component public class InternalAuthenticatedSignatureProvider implements ClinicalSignatureProvider {
  @Override public String type() { return "INTERNAL_AUTHENTICATED_SIGNATURE"; }
}
