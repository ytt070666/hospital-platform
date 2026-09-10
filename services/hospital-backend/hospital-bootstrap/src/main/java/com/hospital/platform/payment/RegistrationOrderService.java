package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegistrationOrderService {
  private final PaymentSupport s;
  private final RegistrationPricingProvider pricing;
  private final long timeout;
  public RegistrationOrderService(PaymentSupport s,RegistrationPricingProvider pricing,
      @Value("${hospital.payment.payment-timeout-seconds:900}") long timeout) {
    if(timeout<1 || timeout>86400) throw new IllegalArgumentException("INVALID_PAYMENT_TIMEOUT");
    this.s=s;this.pricing=pricing;this.timeout=timeout;
  }
  public PaymentViews.Registration ensure(long patient,long appointment) {
    return s.tx.execute(ignored->{
      var rows=s.db.queryForList("select * from appointment where id=? and patient_id=? and deleted=0 for update",appointment,patient);
      if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
      var existing=s.db.queryForList("select * from registration_order where appointment_id=?",appointment);
      if(!existing.isEmpty()) return PaymentViews.registration(existing.getFirst());
      var a=rows.getFirst();
      if(!"BOOKED".equals(a.get("status"))) throw new PaymentException("APPOINTMENT_NOT_BOOKED",409);
      var at=Instant.now();var quote=pricing.quote(appointment,at);var money=quote.money();
      String no=number("RO");String status=money.amountMinor()==0?"PAID":"PENDING_PAYMENT";
      s.db.update("insert into registration_order(order_no,appointment_id,patient_id,member_id,amount_cent,currency,status,pricing_rule_id,pricing_snapshot,payment_deadline,paid_at) values(?,?,?,?,?,?,?,?,?,?,?)",
        no,appointment,patient,a.get("member_id"),money.amountMinor(),money.currency(),status,quote.ruleId(),s.json(quote.snapshot()),Timestamp.from(at.plusSeconds(timeout)),"PAID".equals(status)?Timestamp.from(at):null);
      var result=s.db.queryForMap("select * from registration_order where order_no=?",no);
      var view=PaymentViews.registration(result);
      s.audit("REGISTRATION_CREATED","REGISTRATION_ORDER",view.id(),"PATIENT",patient,"OK",null,view);
      return view;
    });
  }
  public PaymentViews.Registration byAppointment(long patient,long appointment) {
    var rows=s.db.queryForList("select r.* from registration_order r join appointment a on a.id=r.appointment_id where r.appointment_id=? and r.patient_id=? and a.patient_id=? and r.deleted=0",appointment,patient,patient);
    if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
    return PaymentViews.registration(rows.getFirst());
  }
  Map<String,Object> owned(long patient,long registration,boolean lock) {
    var rows=s.db.queryForList("select r.* from registration_order r join appointment a on a.id=r.appointment_id where r.id=? and r.patient_id=? and a.patient_id=? and r.deleted=0"+(lock?" for update":""),registration,patient,patient);
    if(rows.isEmpty()) throw new PaymentException("PAYMENT_RESOURCE_NOT_FOUND",404);
    return rows.getFirst();
  }
  public PaymentViews.Registration detail(long patient,long id) { return PaymentViews.registration(owned(patient,id,false)); }
}
