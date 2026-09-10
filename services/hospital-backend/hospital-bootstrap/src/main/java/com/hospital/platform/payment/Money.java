package com.hospital.platform.payment;

import java.math.BigDecimal;

/** Monetary arithmetic is integral; conversion to major units is presentation-only. */
public record Money(long amountMinor, String currency) {
  public Money {
    if (amountMinor < 0 || amountMinor > 1_000_000_000_000L || !"CNY".equals(currency))
      throw new PaymentException("INVALID_MONEY", 400);
  }
  public static Money ofCent(long amount, String currency) { return new Money(amount, currency); }
  public String display() { return BigDecimal.valueOf(amountMinor, 2).toPlainString() + " " + currency; }
}
