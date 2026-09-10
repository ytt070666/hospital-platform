package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.util.Map;

/** Explicit allowlist: never serialize database entities/provider payloads. */
public final class PaymentViews {
  private PaymentViews() {}
  public record Registration(long id,String orderNo,long appointmentId,long amountCent,String currency,
      String status,long refundedAmountCent,long remainingRefundableCent,String settlementStatus,Object paymentDeadline,Object paidAt,long version) {}
  public record Payment(long id,String paymentNo,long registrationOrderId,String provider,String channel,
      long amountCent,String currency,String status,Object expiresAt,Object successAt,long version) {}
  public record Refund(long id,String refundNo,long registrationOrderId,long paymentOrderId,long appointmentId,long amountCent,String currency,String reasonCode,String status,Object requestedAt,Object successAt,long version) {}
  static Registration registration(Map<String,Object> r) {
    return new Registration(id(r,"id"),string(r,"order_no"),id(r,"appointment_id"),id(r,"amount_cent"),
      string(r,"currency"),string(r,"status"),id(r,"refunded_amount_cent"),id(r,"amount_cent")-id(r,"refunded_amount_cent"),settlement(r),r.get("payment_deadline"),r.get("paid_at"),id(r,"version"));
  }
  static Payment payment(Map<String,Object> r) {
    return new Payment(id(r,"id"),string(r,"payment_no"),id(r,"registration_order_id"),string(r,"provider"),
      string(r,"channel"),id(r,"amount_cent"),string(r,"currency"),string(r,"status"),r.get("expires_at"),r.get("success_at"),id(r,"version"));
  }
  static Refund refund(Map<String,Object> r) { return new Refund(id(r,"id"),string(r,"refund_no"),id(r,"registration_order_id"),id(r,"payment_order_id"),id(r,"appointment_id"),id(r,"refund_amount_cent"),string(r,"currency"),string(r,"reason_code"),string(r,"status"),r.get("requested_at"),r.get("success_at"),id(r,"version")); }
  private static String settlement(Map<String,Object> r) { long refunded=id(r,"refunded_amount_cent"),amount=id(r,"amount_cent");return refunded==0?"PAID":refunded>=amount?"REFUNDED":"PARTIALLY_REFUNDED"; }
}
