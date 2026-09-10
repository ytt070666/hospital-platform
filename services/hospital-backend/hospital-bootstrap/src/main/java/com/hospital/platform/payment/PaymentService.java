package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentService {
  private final PaymentSupport s;
  private final RegistrationOrderService registrations;
  private final PaymentProviderRegistry providers;
  private final RefundService refunds;
  @Autowired public PaymentService(PaymentSupport s,RegistrationOrderService registrations,PaymentProviderRegistry providers,RefundService refunds) {
    this.s=s;this.registrations=registrations;this.providers=providers;this.refunds=refunds;
  }
  /** Preserves isolated legacy service tests; Spring selects the four-argument constructor. */
  PaymentService(PaymentSupport s,RegistrationOrderService registrations,PaymentProviderRegistry providers) {
    this(s,registrations,providers,null);
  }
  public record Create(String provider,String channel) {}
  public PaymentViews.Payment create(long patient,long registration,String key,Create request) {
    if(key==null||!key.matches("[A-Za-z0-9_-]{1,96}")||request==null||request.provider()==null||!request.provider().matches("[A-Z_]{1,24}")||request.channel()==null||!request.channel().matches("[A-Z_]{1,24}"))
      throw new PaymentException("INVALID_PAYMENT_REQUEST",400);
    registrations.owned(patient,registration,false);
    String fingerprint=hash(registration+"|"+request.provider()+"|"+request.channel());
    Map<String,Object> previous=replay(patient,key,fingerprint);
    if(previous!=null) return resume(previous);
    var provider=providers.require(request.provider());
    Map<String,Object> row;
    try { row=s.tx.execute(ignored->{
      var reg=registrations.owned(patient,registration,true);
      var replay=replay(patient,key,fingerprint);if(replay!=null) return replay;
      if(!"PENDING_PAYMENT".equals(reg.get("status"))) throw new PaymentException("REGISTRATION_NOT_PAYABLE",409);
      if(s.db.queryForObject("select count(*) from registration_order where id=? and payment_deadline>current_timestamp(6)",Long.class,registration)!=1)
        throw new PaymentException("PAYMENT_DEADLINE_EXCEEDED",409);
      if(s.db.queryForObject("select count(*) from payment_order where active_registration_id=?",Long.class,registration)>0)
        throw new PaymentException("PAYMENT_ALREADY_ACTIVE",409);
      String no=number("PO");
      s.db.update("insert into payment_order(payment_no,registration_order_id,patient_id,provider,channel,amount_cent,currency,status,client_request_id,request_fingerprint,expires_at) values(?,?,?,?,?,?,?,'CREATED',?,?,?)",
        no,registration,patient,provider.code(),request.channel(),reg.get("amount_cent"),reg.get("currency"),key,fingerprint,reg.get("payment_deadline"));
      var created=s.db.queryForMap("select * from payment_order where payment_no=?",no);
      s.audit("PAYMENT_CREATED","PAYMENT_ORDER",id(created,"id"),"PATIENT",patient,"OK",null,PaymentViews.payment(created));
      return created;
    }); } catch(DuplicateKeyException e) {
      row=replay(patient,key,fingerprint);
      if(row==null) throw new PaymentException("PAYMENT_ALREADY_ACTIVE",409);
    }
    return resume(row);
  }
  private Map<String,Object> replay(long patient,String key,String fingerprint) {
    var rows=s.db.queryForList("select * from payment_order where patient_id=? and client_request_id=?",patient,key);
    if(rows.isEmpty()) return null;
    if(!fingerprint.equals(rows.getFirst().get("request_fingerprint"))) throw new PaymentException("PAYMENT_IDEMPOTENCY_CONFLICT",409);
    return rows.getFirst();
  }
  private PaymentViews.Payment resume(Map<String,Object> row) {
    if(!"CREATED".equals(row.get("status"))) return PaymentViews.payment(row);
    var provider=providers.require(string(row,"provider"));
    s.transaction(id(row,"id"),"PROVIDER_CREATE","REQUESTED","OK");
    var result=provider.createPayment(string(row,"payment_no"),Money.ofCent(id(row,"amount_cent"),string(row,"currency")));
    apply(provider.code(),result,"CREATE");
    return PaymentViews.payment(one(id(row,"id")));
  }
  Map<String,Object> one(long payment) {
    var rows=s.db.queryForList("select * from payment_order where id=?",payment);
    if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
    return rows.getFirst();
  }
  Map<String,Object> owned(long patient,long payment) {
    var row=one(payment);
    if(id(row,"patient_id")!=patient) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
    registrations.owned(patient,id(row,"registration_order_id"),false);return row;
  }
  public PaymentViews.Payment detail(long patient,long payment) { return PaymentViews.payment(owned(patient,payment)); }
  public PaymentViews.Payment query(long patient,long payment) {
    var row=owned(patient,payment);recover(row,false);return detail(patient,payment);
  }
  void recover(Map<String,Object> row,boolean close) {
    var provider=providers.require(string(row,"provider"));String no=string(row,"payment_no");
    s.transaction(id(row,"id"),close?"PROVIDER_CLOSE":"PROVIDER_QUERY","REQUESTED","OK");
    PaymentProvider.Result result;
    try { result=provider.queryPayment(no); }
    catch(PaymentException e) {
      if(!"PROVIDER_ORDER_NOT_FOUND".equals(e.getMessage())||!"CREATED".equals(row.get("status"))) throw e;
      // Stable reference makes retry safe even when create succeeded before a local crash.
      result=provider.createPayment(no,Money.ofCent(id(row,"amount_cent"),string(row,"currency")));
    }
    if(close && Set.of("PENDING","CREATED").contains(result.status())) result=provider.closePayment(no);
    // Adapter returns its authoritative terminal result; callbacks cannot regress this state.
    apply(provider.code(),result,close?"CLOSE":"QUERY");
  }
  void apply(String provider,PaymentProvider.Result result,String operation) {
    try { s.tx.executeWithoutResult(ignored->applyLocked(provider,result,operation)); }
    catch(DuplicateKeyException e) {
      anomaly(result.paymentNo(),"PROVIDER_TRANSACTION_CONFLICT");
      throw new PaymentException("PROVIDER_TRANSACTION_CONFLICT",409);
    } catch(PaymentException e) { anomaly(result.paymentNo(),e.getMessage());throw e; }
  }
  /** Global lock order is RegistrationOrder -> PaymentOrder. Caller owns the short local transaction. */
  void applyLocked(String provider,PaymentProvider.Result result,String operation) {
    var rows=s.db.queryForList("select * from payment_order where payment_no=? and provider=?",result.paymentNo(),provider);
    if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
    var initial=rows.getFirst();long registration=id(initial,"registration_order_id");
    var reg=s.db.queryForMap("select * from registration_order where id=? for update",registration);
    var pay=s.db.queryForMap("select * from payment_order where id=? for update",id(initial,"id"));
    long payment=id(pay,"id");String before=string(pay,"status");String next=result.status();
    if(result.amountCent()!=id(pay,"amount_cent")||result.amountCent()!=id(reg,"amount_cent")) throw new PaymentException("PAYMENT_AMOUNT_MISMATCH",400);
    if(!result.currency().equals(pay.get("currency"))||!result.currency().equals(reg.get("currency"))) throw new PaymentException("PAYMENT_CURRENCY_MISMATCH",400);
    if(!Set.of("PENDING","SUCCESS","FAILED","CLOSED").contains(next)) throw new PaymentException("UNKNOWN_PROVIDER_STATUS",409);
    if("SUCCESS".equals(next) && (result.transactionId()==null||!result.transactionId().matches("[A-Za-z0-9_-]{1,96}")))
      throw new PaymentException("INVALID_PROVIDER_TRANSACTION",400);
    if("SUCCESS".equals(before)) {
      if("SUCCESS".equals(next)&&!result.transactionId().equals(pay.get("provider_transaction_id"))) throw new PaymentException("DUPLICATE_PAYMENT_ANOMALY",409);
      return;
    }
    if("SUCCESS".equals(next)) {
      if("PAID".equals(reg.get("status"))) throw new PaymentException("DUPLICATE_PAYMENT_ANOMALY",409);
      // A verified late success may correct local CLOSED, but never creates a second successful attempt.
      s.db.update("update payment_order set status='SUCCESS',provider_order_no=?,provider_transaction_id=?,success_at=current_timestamp(6),closed_at=null,failed_at=null,version=version+1 where id=? and version=?",
        result.providerOrderNo(),result.transactionId(),payment,pay.get("version"));
      s.db.update("update registration_order set status='PAID',paid_at=current_timestamp(6),closed_at=null,version=version+1 where id=? and status<>'PAID'",registration);
      Long appointment=s.db.queryForObject("select appointment_id from registration_order where id=?",Long.class,registration);
      Long cancelled=s.db.queryForObject("select count(*) from appointment where id=? and status='CANCELLED'",Long.class,appointment);
      if(cancelled!=null&&cancelled>0) afterCommitLatePayment(appointment);
    } else {
      if(Set.of("FAILED","CLOSED").contains(before)||before.equals(next)) return;
      String stamp=switch(next){case "FAILED"->",failed_at=current_timestamp(6)";case "CLOSED"->",closed_at=current_timestamp(6)";default->"";};
      s.db.update("update payment_order set status=?,provider_order_no=?,version=version+1"+stamp+" where id=? and version=?",next,result.providerOrderNo(),payment,pay.get("version"));
    }
    s.transaction(payment,operation,next,"OK");
    s.audit("PAYMENT_"+next,"PAYMENT_ORDER",payment,"PROVIDER",null,"OK",Map.of("status",before),Map.of("status",next));
  }
  private void afterCommitLatePayment(long appointment){if(refunds==null)return;Runnable work=()->refunds.ensureCancelled(appointment,"LATE_PAYMENT","迟到支付自动退款","SYSTEM",null);if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){CompletableFuture.runAsync(work);}});else work.run();}
  void anomaly(String no,String code) {
    var rows=s.db.queryForList("select id from payment_order where payment_no=?",no);
    s.audit("PAYMENT_ANOMALY","PAYMENT_ORDER",rows.isEmpty()?null:id(rows.getFirst(),"id"),"PROVIDER",null,code,null,null);
  }
}
