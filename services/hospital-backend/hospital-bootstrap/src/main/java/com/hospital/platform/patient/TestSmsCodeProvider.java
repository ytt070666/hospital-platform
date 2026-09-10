package com.hospital.platform.patient;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TestSmsCodeProvider implements SmsCodeProvider {
  private final boolean enabled;
  public TestSmsCodeProvider(@Value("${hospital.patient.sms.test-enabled:false}") boolean enabled) { this.enabled = enabled; }
  @Override public SentCode send(String normalizedMobile) {
    if (!enabled) throw new BusinessException(ErrorCode.SMS_CODE_UNAVAILABLE);
    return new SentCode("%06d".formatted(new SecureRandom().nextInt(1_000_000)), 300, true);
  }
}
