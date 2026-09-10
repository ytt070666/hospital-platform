package com.hospital.platform.patient;

/** Provider abstraction intentionally has no production fallback code. */
public interface SmsCodeProvider {
  SentCode send(String normalizedMobile);
  record SentCode(String code, long expiresInSeconds, boolean testOnly) { }
}
