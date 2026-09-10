package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import com.hospital.platform.appointment.AppointmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentTimeoutService {
  private final PaymentSupport s;private final PaymentService payments;private final AppointmentService appointments;private final int batch;private final boolean enabled;
  public PaymentTimeoutService(PaymentSupport s,PaymentService payments,AppointmentService appointments,
      @Value("${hospital.payment.worker-enabled:true}") boolean enabled,@Value("${hospital.payment.batch-size:100}") int batch) {
    this.s=s;this.payments=payments;this.appointments=appointments;this.enabled=enabled;this.batch=Math.clamp(batch,1,500);
  }
  @Scheduled(fixedDelayString="${hospital.payment.worker-interval-ms:30000}")
  public void scheduled() { if(enabled) runBatch(); }
  public int runBatch() {
    int handled=0;
    var due=s.db.queryForList("select * from payment_order where status in ('CREATED','PENDING') and expires_at<=current_timestamp(6) order by expires_at,id limit ?",batch);
    for(var pay:due) {
      try { payments.recover(pay,true);handled++; }
      catch(PaymentException e) { payments.anomaly(string(pay,"payment_no"),e.getMessage()); }
    }
    // Recover create/callback loss before deadline too. Query never creates a new attempt.
    for(var pay:s.db.queryForList("select * from payment_order where status in ('CREATED','PENDING') and expires_at>current_timestamp(6) order by updated_at,id limit ?",batch)) {
      try { payments.recover(pay,false);s.db.update("update payment_order set updated_at=current_timestamp(6) where id=?",pay.get("id")); }
      catch(PaymentException e) { payments.anomaly(string(pay,"payment_no"),e.getMessage()); }
    }
    for(Long id:s.db.queryForList("select id from registration_order where status='PENDING_PAYMENT' and payment_deadline<=current_timestamp(6) order by payment_deadline,id limit ?",Long.class,batch)) {
      Long appointment=s.tx.execute(ignored->{
        var reg=s.db.queryForMap("select * from registration_order where id=? for update",id);
        if(!"PENDING_PAYMENT".equals(reg.get("status"))) return null;
        if(s.db.queryForObject("select count(*) from payment_order where registration_order_id=? and status in ('CREATED','PENDING','SUCCESS')",Long.class,id)>0) return null;
        s.db.update("update registration_order set status='CLOSED',closed_at=current_timestamp(6),version=version+1 where id=? and status='PENDING_PAYMENT'",id);
        s.audit("REGISTRATION_CLOSED","REGISTRATION_ORDER",id,"SYSTEM",null,"DEADLINE",null,null);
        return id(reg,"appointment_id");
      });
      if(appointment!=null&&s.db.queryForObject("select count(*) from visit_encounter where appointment_id=? and deleted=0",Long.class,appointment)==0) appointments.cancelBySystem(appointment,"PAYMENT_TIMEOUT","支付超时未支付");
    }
    return handled;
  }
}
