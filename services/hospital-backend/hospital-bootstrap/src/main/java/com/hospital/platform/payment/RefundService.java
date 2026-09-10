package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Refunds are independent, durable facts. Payment SUCCESS is intentionally never rewritten. */
@Service
public class RefundService {
  private final PaymentSupport s; private final PaymentProviderRegistry providers;
  public RefundService(PaymentSupport s,PaymentProviderRegistry providers){this.s=s;this.providers=providers;}

  public PaymentViews.Refund ensureCancelled(long appointment,String reasonCode,String reason,String actorType,Long actorId) {
    Map<String,Object> created;
    try { created=s.tx.execute(ignored->{
      var rows=s.db.queryForList("select r.*,p.id payment_id,p.payment_no,p.provider,p.provider_transaction_id,p.status payment_status,a.status appointment_status from registration_order r join appointment a on a.id=r.appointment_id join payment_order p on p.registration_order_id=r.id and p.status='SUCCESS' where r.appointment_id=? for update",appointment);
      if(rows.isEmpty()||!"CANCELLED".equals(rows.getFirst().get("appointment_status"))) return null;
      var r=rows.getFirst(); String key="APPOINTMENT_CANCEL:"+appointment+":"+id(r,"payment_id");
      var old=s.db.queryForList("select * from refund_order where refund_business_key=?",key); if(!old.isEmpty()) return old.getFirst();
      long amount=id(r,"amount_cent")-id(r,"refunded_amount_cent")-id(r,"refund_reserved_amount_cent"); if(amount<=0) return null;
      String no=number("RF");
      s.db.update("insert into refund_order(refund_no,refund_business_key,registration_order_id,payment_order_id,appointment_id,patient_id,provider,provider_transaction_id,refund_amount_cent,currency,reason_code,reason,status) values(?,?,?,?,?,?,?,?,?,?,?,?,'CREATED')",no,key,id(r,"id"),id(r,"payment_id"),appointment,id(r,"patient_id"),string(r,"provider"),string(r,"provider_transaction_id"),amount,string(r,"currency"),reasonCode,reason);
      s.db.update("update registration_order set refund_reserved_amount_cent=refund_reserved_amount_cent+?,version=version+1 where id=? and refunded_amount_cent+refund_reserved_amount_cent+?<=amount_cent",amount,id(r,"id"),amount);
      var result=s.db.queryForMap("select * from refund_order where refund_no=?",no);
      s.audit("REFUND_CREATED","REFUND_ORDER",id(result,"id"),actorType,actorId,"OK",null,PaymentViews.refund(result)); return result;
    }); } catch(DuplicateKeyException ex) { return detailByAppointment(appointment); }
    if(created==null)return null; request(created); return PaymentViews.refund(one(id(created,"id")));
  }
  private PaymentViews.Refund detailByAppointment(long appointment){var rows=s.db.queryForList("select * from refund_order where appointment_id=? order by id limit 1",appointment);return rows.isEmpty()?null:PaymentViews.refund(rows.getFirst());}
  public PaymentViews.Refund retry(long refundId,long actor){var row=one(refundId); if(!"FAILED".equals(row.get("status"))) throw new PaymentException("REFUND_NOT_RETRYABLE",409); s.tx.executeWithoutResult(x->{var r=s.db.queryForMap("select * from refund_order where id=? for update",refundId);s.db.update("update registration_order set refund_reserved_amount_cent=refund_reserved_amount_cent+?,version=version+1 where id=? and refunded_amount_cent+refund_reserved_amount_cent+?<=amount_cent",id(r,"refund_amount_cent"),id(r,"registration_order_id"),id(r,"refund_amount_cent"));s.db.update("update refund_order set status='CREATED',failed_at=null,version=version+1 where id=?",refundId);s.audit("REFUND_RETRY","REFUND_ORDER",refundId,"ADMIN",actor,"OK",Map.of("status","FAILED"),Map.of("status","CREATED"));});request(one(refundId));return PaymentViews.refund(one(refundId));}
  public void request(Map<String,Object> row){if(row==null||"SUCCESS".equals(row.get("status")))return;var provider=providers.require(string(row,"provider"));PaymentProvider.RefundResult result=provider.createRefund(string(row,"refund_no"),paymentNo(id(row,"payment_order_id")),Money.ofCent(id(row,"refund_amount_cent"),string(row,"currency")));apply(provider.code(),result,"REFUND_REQUEST");}
  public void recover(long id){var row=one(id);if("SUCCESS".equals(row.get("status")))return;var provider=providers.require(string(row,"provider"));apply(provider.code(),provider.queryRefund(string(row,"refund_no")),"REFUND_QUERY");}
  private String paymentNo(long payment){return s.db.queryForObject("select payment_no from payment_order where id=?",String.class,payment);}
  public void apply(String provider,PaymentProvider.RefundResult result,String operation){s.tx.executeWithoutResult(x->applyLocked(provider,result,operation));}
  void applyLocked(String provider,PaymentProvider.RefundResult result,String operation){
    var rows=s.db.queryForList("select * from refund_order where refund_no=? and provider=?",result.refundNo(),provider);if(rows.isEmpty())throw new PaymentException("REFUND_RESOURCE_NOT_FOUND",404);
    var refund=s.db.queryForMap("select * from refund_order where id=? for update",id(rows.getFirst(),"id"));var reg=s.db.queryForMap("select * from registration_order where id=? for update",id(refund,"registration_order_id"));
    if(result.amountCent()!=id(refund,"refund_amount_cent")||!result.currency().equals(refund.get("currency"))||!result.paymentNo().equals(paymentNo(id(refund,"payment_order_id"))))throw new PaymentException("REFUND_AMOUNT_MISMATCH",400);
    String before=string(refund,"status"),next=result.status();if(!List.of("PENDING","SUCCESS","FAILED","CLOSED").contains(next))throw new PaymentException("UNKNOWN_REFUND_STATUS",409);if("SUCCESS".equals(before))return;
    if("SUCCESS".equals(next)){int n=s.db.update("update registration_order set refunded_amount_cent=refunded_amount_cent+?,refund_reserved_amount_cent=refund_reserved_amount_cent-?,version=version+1 where id=? and refunded_amount_cent+?<=amount_cent and refund_reserved_amount_cent>=?",id(refund,"refund_amount_cent"),id(refund,"refund_amount_cent"),id(reg,"id"),id(refund,"refund_amount_cent"),id(refund,"refund_amount_cent"));if(n!=1)throw new PaymentException("REFUND_AMOUNT_EXCEEDED",409);}
    else if(List.of("FAILED","CLOSED").contains(next)&&List.of("CREATED","PENDING").contains(before))s.db.update("update registration_order set refund_reserved_amount_cent=refund_reserved_amount_cent-?,version=version+1 where id=? and refund_reserved_amount_cent>=?",id(refund,"refund_amount_cent"),id(reg,"id"),id(refund,"refund_amount_cent"));
    String stamp=switch(next){case "SUCCESS"->"success_at=current_timestamp(6)";case "FAILED"->"failed_at=current_timestamp(6)";case "CLOSED"->"closed_at=current_timestamp(6)";default->"requested_at=current_timestamp(6)";};
    s.db.update("update refund_order set status=?,provider_refund_no=?,"+stamp+",version=version+1 where id=?",next,result.providerRefundNo(),id(refund,"id"));s.audit("REFUND_"+next,"REFUND_ORDER",id(refund,"id"),"PROVIDER",null,"OK",Map.of("status",before),Map.of("status",next));
  }
  public PaymentViews.Refund patientDetail(long patient,long id){var r=one(id);if(id(r,"patient_id")!=patient)throw new PaymentException("REFUND_RESOURCE_NOT_FOUND",404);return PaymentViews.refund(r);}
  Map<String,Object> one(long id){var rows=s.db.queryForList("select * from refund_order where id=?",id);if(rows.isEmpty())throw new PaymentException("REFUND_RESOURCE_NOT_FOUND",404);return rows.getFirst();}
  @Scheduled(fixedDelayString="${hospital.payment.worker-interval-ms:30000}") public void recoverPending(){for(Long id:s.db.queryForList("select id from refund_order where status in ('CREATED','PENDING') order by updated_at,id limit 100",Long.class)){try{var r=one(id);if("CREATED".equals(r.get("status")))request(r);else recover(id);}catch(PaymentException ignored){}}}
  @Scheduled(fixedDelayString="${hospital.payment.worker-interval-ms:30000}") public void recoverCancelled(){for(Long id:s.db.queryForList("select a.id from appointment a join registration_order r on r.appointment_id=a.id join payment_order p on p.registration_order_id=r.id and p.status='SUCCESS' left join refund_order f on f.appointment_id=a.id and f.payment_order_id=p.id where a.status='CANCELLED' and f.id is null order by a.id limit 100",Long.class))ensureCancelled(id,"LATE_PAYMENT","自动退款","SYSTEM",null);}
}
