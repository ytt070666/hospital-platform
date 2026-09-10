package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local durable simulator, never a real payment gateway. Terminal provider states cannot be rewritten. */
@Component
@Profile("!prod & (dev | test)")
public class TestPaymentProvider implements PaymentProvider {
  private final PaymentSupport s;
  private final byte[] secret;
  public TestPaymentProvider(PaymentSupport s,@Value("${hospital.payment.test-signing-key:}") String secret) {
    this.s=s;
    byte[] decoded;
    try { decoded=Base64.getDecoder().decode(secret); } catch(IllegalArgumentException e) { decoded=new byte[0]; }
    this.secret=decoded;
  }
  @Override public String code() { return "TEST"; }
  @Override public boolean configured() { return secret.length>=32; }
  private void guard() {
    if(!configured()) throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);
    if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("PROVIDER_CALL_INSIDE_TRANSACTION");
  }
  @Override public Result createPayment(String no,Money money) {
    guard();
    s.db.update("insert into test_payment_provider_order(payment_no,amount_cent,currency) values(?,?,?) on duplicate key update payment_no=payment_no",no,money.amountMinor(),money.currency());
    var found=queryPayment(no);
    if(found.amountCent()!=money.amountMinor()||!found.currency().equals(money.currency())) throw new PaymentException("PROVIDER_REFERENCE_CONFLICT",409);
    return found;
  }
  @Override public Result queryPayment(String no) {
    guard();
    var rows=s.db.queryForList("select * from test_payment_provider_order where payment_no=?",no);
    if(rows.isEmpty()) throw new PaymentException("PROVIDER_ORDER_NOT_FOUND",404);
    var r=rows.getFirst();
    return new Result(no,no,string(r,"transaction_id"),id(r,"amount_cent"),string(r,"currency"),string(r,"status"));
  }
  @Override public Result closePayment(String no) {
    guard();
    s.db.update("update test_payment_provider_order set status='CLOSED' where payment_no=? and status='PENDING'",no);
    return queryPayment(no);
  }
  public Result simulate(String no,String status) {
    guard();
    if(!java.util.Set.of("SUCCESS","FAILED","PENDING","TIMEOUT").contains(status)) throw new PaymentException("INVALID_TEST_SCENARIO",400);
    if("SUCCESS".equals(status)) s.db.update("update test_payment_provider_order set status='SUCCESS',transaction_id=? where payment_no=? and status in ('PENDING','CLOSED')",number("TESTTX"),no);
    if("FAILED".equals(status)) s.db.update("update test_payment_provider_order set status='FAILED' where payment_no=? and status='PENDING'",no);
    if("TIMEOUT".equals(status)) return closePayment(no);
    return queryPayment(no);
  }
  @Override public RefundResult createRefund(String refundNo,String paymentNo,Money money) {
    guard();
    s.db.update("insert into test_refund_provider_order(refund_no,payment_no,amount_cent,currency,provider_refund_no) values(?,?,?,?,?) on duplicate key update payment_no=values(payment_no),amount_cent=values(amount_cent),currency=values(currency),status=if(status='FAILED','PENDING',status)",refundNo,paymentNo,money.amountMinor(),money.currency(),"TR"+refundNo);
    return queryRefund(refundNo);
  }
  @Override public RefundResult queryRefund(String refundNo) {
    guard(); var rows=s.db.queryForList("select * from test_refund_provider_order where refund_no=?",refundNo);
    if(rows.isEmpty()) throw new PaymentException("PROVIDER_REFUND_NOT_FOUND",404);
    var r=rows.getFirst(); return new RefundResult(refundNo,string(r,"provider_refund_no"),string(r,"payment_no"),id(r,"amount_cent"),string(r,"currency"),string(r,"status"));
  }
  public RefundResult simulateRefund(String no,String status) {
    guard(); if(!java.util.Set.of("SUCCESS","FAILED","PENDING","CLOSED").contains(status)) throw new PaymentException("INVALID_TEST_SCENARIO",400);
    s.db.update("update test_refund_provider_order set status=? where refund_no=? and status='PENDING'",status,no); return queryRefund(no);
  }
  public record SignedCallback(String body,Signature headers) {}
  public SignedCallback signed(Callback callback) {
    guard();
    String body=s.json(callback);String timestamp=Long.toString(Instant.now().getEpochSecond());String nonce=number("N");
    return new SignedCallback(body,new Signature(timestamp,nonce,sign(body.getBytes(StandardCharsets.UTF_8),timestamp,nonce)));
  }
  public SignedCallback signedRefund(RefundCallback callback) {
    guard(); String body=s.json(callback);String timestamp=Long.toString(Instant.now().getEpochSecond());String nonce=number("N");
    return new SignedCallback(body,new Signature(timestamp,nonce,sign(body.getBytes(StandardCharsets.UTF_8),timestamp,nonce)));
  }
  @Override public boolean verifyCallback(byte[] raw,Signature signature) {
    guard();
    try {
      if(signature==null||signature.timestamp()==null||!signature.timestamp().matches("[0-9]{10,12}")||signature.nonce()==null||!signature.nonce().matches("[A-Za-z0-9_-]{16,96}")||signature.signature()==null) return false;
      long seconds=Long.parseLong(signature.timestamp());
      if(Math.abs(Instant.now().getEpochSecond()-seconds)>300) return false;
      byte[] actual=Base64.getDecoder().decode(signature.signature());
      byte[] expected=Base64.getDecoder().decode(sign(raw,signature.timestamp(),signature.nonce()));
      return MessageDigest.isEqual(expected,actual);
    } catch(IllegalArgumentException e) { return false; }
  }
  private String sign(byte[] raw,String timestamp,String nonce) {
    try {
      Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
      mac.update((timestamp+"\n"+nonce+"\n").getBytes(StandardCharsets.UTF_8));mac.update(raw);
      return Base64.getEncoder().encodeToString(mac.doFinal());
    } catch(Exception e) { throw new PaymentException("CALLBACK_SIGNING_UNAVAILABLE",503); }
  }
  @Override public Callback parseCallback(byte[] raw) {
    try { return s.json.readerFor(Callback.class).without(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(raw); }
    catch(Exception e) { throw new PaymentException("INVALID_CALLBACK_BODY",400); }
  }
  @Override public RefundCallback parseRefundCallback(byte[] raw) {
    try { return s.json.readerFor(RefundCallback.class).without(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(raw); }
    catch(Exception e) { throw new PaymentException("INVALID_REFUND_CALLBACK_BODY",400); }
  }
}
