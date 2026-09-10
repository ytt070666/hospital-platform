package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentCallbackService {
  private final PaymentSupport s;
  private final PaymentService payments;
  private final PaymentProviderRegistry providers;
  private final int perMinute;
  private long window=System.currentTimeMillis()/60000;
  private int requests;
  public PaymentCallbackService(PaymentSupport s,PaymentService payments,PaymentProviderRegistry providers,
      @Value("${hospital.payment.callback-per-minute:1000}") int perMinute) {
    this.s=s;this.payments=payments;this.providers=providers;this.perMinute=Math.max(100,perMinute);
  }
  private synchronized void rate() {
    long now=System.currentTimeMillis()/60000;if(now!=window){window=now;requests=0;}
    if(++requests>perMinute) throw new PaymentException("CALLBACK_RATE_LIMITED",429);
  }
  public void accept(String code,byte[] raw,PaymentProvider.Signature signature) {
    rate();
    if(raw.length>16384) throw new PaymentException("CALLBACK_TOO_LARGE",413);
    var provider=providers.require(code);
    if(!provider.verifyCallback(raw,signature)) {
      s.audit("CALLBACK_REJECTED","CALLBACK",null,"PROVIDER",null,"INVALID_CALLBACK_SIGNATURE",null,Map.of("payloadHash",hash(raw)));
      throw new PaymentException("INVALID_CALLBACK_SIGNATURE",401);
    }
    var callback=provider.parseCallback(raw);
    if(callback.eventId()==null||!callback.eventId().matches("[A-Za-z0-9_-]{1,96}")||callback.paymentNo()==null||!callback.paymentNo().matches("[A-Za-z0-9_-]{1,64}")||callback.currency()==null||callback.status()==null||
       (callback.transactionId()!=null&&!callback.transactionId().matches("[A-Za-z0-9_-]{1,96}")))
      throw new PaymentException("INVALID_CALLBACK_BODY",400);
    String digest=hash(raw);
    try {
      s.db.update("insert into payment_callback_event(provider,event_id,nonce,payment_no,provider_transaction_id,signature_verified,processing_status,payload_hash,trace_id) values(?,?,?,?,?,true,'RECEIVED',?,?)",
        code,callback.eventId(),signature.nonce(),callback.paymentNo(),callback.transactionId(),digest,trace());
    } catch(DuplicateKeyException e) {
      var events=s.db.queryForList("select payload_hash,processing_status,result_code from payment_callback_event where provider=? and event_id=?",code,callback.eventId());
      if(events.isEmpty()||!digest.equals(events.getFirst().get("payload_hash"))) {
        payments.anomaly(callback.paymentNo(),"CALLBACK_REPLAY_CONFLICT");throw new PaymentException("CALLBACK_REPLAY_CONFLICT",409);
      }
      var event=events.getFirst();
      if("PROCESSED".equals(event.get("processing_status"))) return;
      if("REJECTED".equals(event.get("processing_status"))) throw new PaymentException(string(event,"result_code"),400);
    }
    try {
      var rows=s.db.queryForList("select * from payment_order where payment_no=? and provider=?",callback.paymentNo(),code);
      if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
      var pay=rows.getFirst();
      if(callback.amountCent()!=id(pay,"amount_cent")) throw new PaymentException("PAYMENT_AMOUNT_MISMATCH",400);
      if(!callback.currency().equals(pay.get("currency"))) throw new PaymentException("PAYMENT_CURRENCY_MISMATCH",400);
      if(!java.util.Set.of("SUCCESS","PENDING","FAILED","CLOSED").contains(callback.status())) throw new PaymentException("UNKNOWN_PROVIDER_STATUS",409);
      // Query occurs outside all local transactions, so close/callback races use provider truth.
      var truth=provider.queryPayment(callback.paymentNo());
      if("SUCCESS".equals(callback.status())&&(!"SUCCESS".equals(truth.status())||!java.util.Objects.equals(callback.transactionId(),truth.transactionId())))
        throw new PaymentException("CALLBACK_PROVIDER_DISAGREEMENT",409);
      s.tx.executeWithoutResult(ignored->{
        var event=s.db.queryForMap("select * from payment_callback_event where provider=? and event_id=? for update",code,callback.eventId());
        if("PROCESSED".equals(event.get("processing_status"))) return;
        payments.applyLocked(code,truth,"CALLBACK");
        s.db.update("update payment_callback_event set processing_status='PROCESSED',processed_at=current_timestamp(6),result_code='OK' where id=?",event.get("id"));
        s.audit("CALLBACK_VERIFIED","PAYMENT_ORDER",id(pay,"id"),"PROVIDER",null,"OK",null,Map.of("payloadHash",digest));
      });
    } catch(DuplicateKeyException e) { reject(code,callback,"PROVIDER_TRANSACTION_CONFLICT");throw new PaymentException("PROVIDER_TRANSACTION_CONFLICT",409); }
    catch(PaymentException e) {
      // Unavailable/query-not-found is retriable: keep RECEIVED for callback redelivery.
      if(e.status()<500&&!"PROVIDER_ORDER_NOT_FOUND".equals(e.getMessage())) reject(code,callback,e.getMessage());
      throw e;
    }
  }
  private void reject(String provider,PaymentProvider.Callback event,String code) {
    s.db.update("update payment_callback_event set processing_status='REJECTED',result_code=?,processed_at=current_timestamp(6) where provider=? and event_id=? and processing_status='RECEIVED'",code,provider,event.eventId());
    payments.anomaly(event.paymentNo(),code);
  }
  /** A verified durable event can be completed after a process crash without retaining raw sensitive provider payloads. */
  @Scheduled(fixedDelayString="${hospital.payment.worker-interval-ms:30000}")
  public void recoverReceived() {
    for(var event:s.db.queryForList("select provider,event_id,payment_no from payment_callback_event where processing_status in ('RECEIVED','FAILED_RETRYABLE') order by received_at,id limit 100")) {
      try {var provider=providers.require(string(event,"provider"));var truth=provider.queryPayment(string(event,"payment_no"));s.tx.executeWithoutResult(ignored->{var locked=s.db.queryForMap("select * from payment_callback_event where provider=? and event_id=? for update",event.get("provider"),event.get("event_id"));if("PROCESSED".equals(locked.get("processing_status")))return;payments.applyLocked(provider.code(),truth,"CALLBACK_RECOVERY");s.db.update("update payment_callback_event set processing_status='PROCESSED',processed_at=current_timestamp(6),result_code='RECOVERED' where id=?",locked.get("id"));});}
      catch(PaymentException ignored) { s.db.update("update payment_callback_event set processing_status='FAILED_RETRYABLE',result_code='RECOVERY_RETRY' where provider=? and event_id=? and processing_status='RECEIVED'",event.get("provider"),event.get("event_id")); }
    }
  }
}
