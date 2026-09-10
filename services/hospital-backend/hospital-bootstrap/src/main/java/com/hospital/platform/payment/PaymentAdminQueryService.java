package com.hospital.platform.payment;

import com.hospital.platform.common.api.PageResponse;
import com.hospital.platform.iam.application.DataScopeQueryService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentAdminQueryService {
  private final PaymentSupport s;private final DataScopeQueryService scopes;
  public PaymentAdminQueryService(PaymentSupport s,DataScopeQueryService scopes) { this.s=s;this.scopes=scopes; }
  public PageResponse<?> list(long actor,boolean payment,long page,long size,String status) {
    page=Math.clamp(page,1,100000);size=Math.clamp(size,1,100);
    var allowed=scopes.visibleAdministrativeDepartmentIds(actor);
    if(allowed.isEmpty()) return new PageResponse<>(page,size,0,List.of());
    String sql=joins(payment)+" where r.deleted=0 and d.administrative_department_id in ("+String.join(",",Collections.nCopies(allowed.size(),"?"))+")";
    var args=new ArrayList<Object>(allowed);
    if(status!=null&&!status.isBlank()){sql+=" and "+(payment?"p":"r")+".status=?";args.add(status);}
    long total=s.db.queryForObject("select count(*)"+sql,Long.class,args.toArray());
    args.add(size);args.add((page-1)*size);
    var rows=s.db.queryForList("select "+(payment?"p":"r")+".*"+sql+" order by "+(payment?"p":"r")+".id desc limit ? offset ?",args.toArray());
    return new PageResponse<>(page,size,total,rows.stream().map(r->payment?PaymentViews.payment(r):PaymentViews.registration(r)).toList());
  }
  public Object detail(long actor,boolean payment,long id) {
    var allowed=scopes.visibleAdministrativeDepartmentIds(actor);
    if(allowed.isEmpty()) throw new PaymentException("FORBIDDEN",403);
    var args=new ArrayList<Object>();args.add(id);args.addAll(allowed);
    var rows=s.db.queryForList("select "+(payment?"p":"r")+".*"+joins(payment)+" where "+(payment?"p":"r")+".id=? and r.deleted=0 and d.administrative_department_id in ("+String.join(",",Collections.nCopies(allowed.size(),"?"))+")",args.toArray());
    if(rows.isEmpty()) throw new PaymentException("FORBIDDEN",403);
    s.audit("ADMIN_PAYMENT_VIEW",payment?"PAYMENT_ORDER":"REGISTRATION_ORDER",id,"ADMIN",actor,"OK",null,null);
    return payment?PaymentViews.payment(rows.getFirst()):PaymentViews.registration(rows.getFirst());
  }
  private String joins(boolean payment) {
    return (payment?" from payment_order p join registration_order r on r.id=p.registration_order_id":" from registration_order r")+
      " join appointment a on a.id=r.appointment_id join doctor_schedule ds on ds.id=a.schedule_id join outpatient_department od on od.id=ds.outpatient_department_id join department d on d.id=od.department_id";
  }
}
