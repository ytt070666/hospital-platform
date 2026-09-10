package com.hospital.platform.payment;

import java.time.Instant;
import java.util.Map;

public interface RegistrationPricingProvider {
  Quote quote(long appointmentId, Instant at);
  record Quote(Money money, Long ruleId, Map<String,Object> snapshot) {}
}
