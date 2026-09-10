package com.hospital.platform.payment;

import com.hospital.platform.iam.application.DataScopeQueryService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Finance aggregates use the same organisational SQL scope as order lists. */
@Service
public class FinanceDashboardService {
  private final PaymentSupport s; private final DataScopeQueryService scopes;
  public FinanceDashboardService(PaymentSupport s,DataScopeQueryService scopes){this.s=s;this.scopes=scopes;}
  public Map<String,Object> overview(long actor){
    List<Long> ids=scopes.visibleAdministrativeDepartmentIds(actor);if(ids.isEmpty())return Map.of("paidAmountCent",0,"pendingAmountCent",0,"refundedAmountCent",0,"paymentCount",0,"refundCount",0,"successfulPaymentCount",0,"pendingPaymentCount",0,"refundPendingCount",0,"refundSuccessCount",0,"anomalyCount",0);
    String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
    String scope=" from registration_order r join appointment a on a.id=r.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where r.deleted=0 and d.administrative_department_id in ("+marks+")";
    Object[] args=ids.toArray();
    Long paid=s.db.queryForObject("select coalesce(sum(case when r.status='PAID' then r.amount_cent else 0 end),0)"+scope,Long.class,args);
    Long pending=s.db.queryForObject("select coalesce(sum(case when r.status='PENDING_PAYMENT' then r.amount_cent else 0 end),0)"+scope,Long.class,args);
    Long refunded=s.db.queryForObject("select coalesce(sum(r.refunded_amount_cent),0)"+scope,Long.class,args);
    Long count=s.db.queryForObject("select count(*)"+scope,Long.class,args);
    Long refunds=s.db.queryForObject("select count(*) from refund_order f join appointment a on a.id=f.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where d.administrative_department_id in ("+marks+")",Long.class,args);
    Long paidCount=s.db.queryForObject("select count(*)"+scope+" and r.status='PAID'",Long.class,args);
    Long pendingCount=s.db.queryForObject("select count(*)"+scope+" and r.status='PENDING_PAYMENT'",Long.class,args);
    String refundScope=" from refund_order f join appointment a on a.id=f.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where d.administrative_department_id in ("+marks+")";
    Long refundPending=s.db.queryForObject("select count(*)"+refundScope+" and f.status in ('CREATED','PENDING')",Long.class,args);
    Long refundSuccess=s.db.queryForObject("select count(*)"+refundScope+" and f.status='SUCCESS'",Long.class,args);
    Long anomalies=s.db.queryForObject("select count(*) from payment_reconciliation_record x where x.status='OPEN' and "+reconciliationScope("x",marks),Long.class,reconciliationArgs(ids));
    return Map.of("paidAmountCent",paid,"pendingAmountCent",pending,"refundedAmountCent",refunded,"paymentCount",count,"refundCount",refunds,"successfulPaymentCount",paidCount,"pendingPaymentCount",pendingCount,"refundPendingCount",refundPending,"refundSuccessCount",refundSuccess,"anomalyCount",anomalies);
  }
  private static String reconciliationScope(String x,String marks){
    String department="d.administrative_department_id in ("+marks+")";
    return "(("+x+".resource_type='PAYMENT_ORDER' and exists(select 1 from payment_order p join registration_order r on r.id=p.registration_order_id join appointment a on a.id=r.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where p.id="+x+".resource_id and "+department+"))"
      +" or ("+x+".resource_type='REGISTRATION_ORDER' and exists(select 1 from registration_order r join appointment a on a.id=r.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where r.id="+x+".resource_id and "+department+"))"
      +" or ("+x+".resource_type='APPOINTMENT' and exists(select 1 from appointment a join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id where a.id="+x+".resource_id and "+department+")))";
  }
  private static Object[] reconciliationArgs(List<Long> ids){List<Object> values=new ArrayList<>();for(int i=0;i<3;i++)values.addAll(ids);return values.toArray();}
}
