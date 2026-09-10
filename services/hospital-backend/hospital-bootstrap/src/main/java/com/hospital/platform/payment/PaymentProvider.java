package com.hospital.platform.payment;

/** Adapters must use paymentNo as the durable, idempotent merchant reference. No caller transaction may be open. */
public interface PaymentProvider {
  String code();
  boolean configured();
  Result createPayment(String paymentNo, Money money);
  Result queryPayment(String paymentNo);
  Result closePayment(String paymentNo);
  default RefundResult createRefund(String refundNo,String paymentNo,Money money) { throw new PaymentException("REFUND_PROVIDER_NOT_CONFIGURED",503); }
  default RefundResult queryRefund(String refundNo) { throw new PaymentException("REFUND_PROVIDER_NOT_CONFIGURED",503); }
  boolean verifyCallback(byte[] rawBody, Signature signature);
  Callback parseCallback(byte[] rawBody);
  default boolean verifyRefundCallback(byte[] rawBody,Signature signature) { return verifyCallback(rawBody,signature); }
  default RefundCallback parseRefundCallback(byte[] rawBody) { throw new PaymentException("INVALID_REFUND_CALLBACK_BODY",400); }
  record Result(String paymentNo,String providerOrderNo,String transactionId,long amountCent,String currency,String status) {}
  record Callback(String eventId,String paymentNo,String transactionId,long amountCent,String currency,String status) {
    Result result() { return new Result(paymentNo,paymentNo,transactionId,amountCent,currency,status); }
  }
  record RefundResult(String refundNo,String providerRefundNo,String paymentNo,long amountCent,String currency,String status) {}
  record RefundCallback(String eventId,String refundNo,String providerRefundNo,String paymentNo,long amountCent,String currency,String status) {}
  /** Raw transport signature fields; keyId is the provider certificate serial/key identifier when supplied. */
  record Signature(String timestamp,String nonce,String signature,String keyId) {
    Signature(String timestamp,String nonce,String signature) { this(timestamp,nonce,signature,null); }
  }
}
