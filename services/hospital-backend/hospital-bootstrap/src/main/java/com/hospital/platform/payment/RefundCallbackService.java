package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service public class RefundCallbackService {
  private final PaymentSupport s; private final PaymentProviderRegistry providers; private final RefundService refunds;
  public RefundCallbackService(PaymentSupport s,PaymentProviderRegistry providers,RefundService refunds){this.s=s;this.providers=providers;this.refunds=refunds;}
  public void accept(String code,byte[] raw,PaymentProvider.Signature signature){
    if(raw.length>16384)throw new PaymentException("CALLBACK_TOO_LARGE",413);var p=providers.require(code);if(!p.verifyRefundCallback(raw,signature))throw new PaymentException("INVALID_REFUND_CALLBACK_SIGNATURE",401);
    var e=p.parseRefundCallback(raw);if(e.eventId()==null||e.refundNo()==null||e.currency()==null||e.status()==null)throw new PaymentException("INVALID_REFUND_CALLBACK_BODY",400);String digest=hash(raw);
    try{s.db.update("insert into refund_callback_event(provider,event_id,nonce,refund_no,provider_refund_no,signature_verified,processing_status,payload_hash,trace_id) values(?,?,?,?,?,true,'RECEIVED',?,?)",code,e.eventId(),signature.nonce(),e.refundNo(),e.providerRefundNo(),digest,trace());}
    catch(DuplicateKeyException ex){var rows=s.db.queryForList("select payload_hash,processing_status from refund_callback_event where provider=? and event_id=?",code,e.eventId());if(rows.isEmpty()||!digest.equals(rows.getFirst().get("payload_hash")))throw new PaymentException("REFUND_CALLBACK_REPLAY_CONFLICT",409);if("PROCESSED".equals(rows.getFirst().get("processing_status")))return;}
    try {var truth=p.queryRefund(e.refundNo());if(truth.amountCent()!=e.amountCent()||!truth.currency().equals(e.currency()))throw new PaymentException("REFUND_AMOUNT_MISMATCH",400);s.tx.executeWithoutResult(x->{var event=s.db.queryForMap("select * from refund_callback_event where provider=? and event_id=? for update",code,e.eventId());if("PROCESSED".equals(event.get("processing_status")))return;refunds.applyLocked(code,truth,"REFUND_CALLBACK");s.db.update("update refund_callback_event set processing_status='PROCESSED',result_code='OK',processed_at=current_timestamp(6) where id=?",id(event,"id"));});}
    catch(PaymentException ex){if(ex.status()<500)s.db.update("update refund_callback_event set processing_status='REJECTED',result_code=?,processed_at=current_timestamp(6) where provider=? and event_id=? and processing_status='RECEIVED'",ex.getMessage(),code,e.eventId());throw ex;}
  }
  @Scheduled(fixedDelayString="${hospital.payment.worker-interval-ms:30000}") public void recoverReceived(){for(var event:s.db.queryForList("select provider,event_id,refund_no from refund_callback_event where processing_status in ('RECEIVED','FAILED_RETRYABLE') order by received_at,id limit 100")){try{var provider=providers.require(string(event,"provider"));var truth=provider.queryRefund(string(event,"refund_no"));s.tx.executeWithoutResult(x->{var locked=s.db.queryForMap("select * from refund_callback_event where provider=? and event_id=? for update",event.get("provider"),event.get("event_id"));if("PROCESSED".equals(locked.get("processing_status")))return;refunds.applyLocked(provider.code(),truth,"REFUND_CALLBACK_RECOVERY");s.db.update("update refund_callback_event set processing_status='PROCESSED',processed_at=current_timestamp(6),result_code='RECOVERED' where id=?",locked.get("id"));});}catch(PaymentException ignored){s.db.update("update refund_callback_event set processing_status='FAILED_RETRYABLE',result_code='RECOVERY_RETRY' where provider=? and event_id=? and processing_status='RECEIVED'",event.get("provider"),event.get("event_id"));}}}
}
