package com.hospital.platform.payment;

public class PaymentException extends RuntimeException {
  private final int status;
  public PaymentException(String code, int status) { super(code); this.status = status; }
  public int status() { return status; }
}
