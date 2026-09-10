package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocalRegistrationPricingProvider implements RegistrationPricingProvider {
  private final JdbcTemplate db;
  public LocalRegistrationPricingProvider(JdbcTemplate db) { this.db=db; }
  @Override public Quote quote(long appointmentId, Instant at) {
    var contexts=db.queryForList("""
      select c.hospital_id,s.campus_id,o.department_id,s.clinic_type_id,d.title_id,
        coalesce(ct.payment_required,0) payment_required
      from appointment a join doctor_schedule s on s.id=a.schedule_id
      join hospital_campus c on c.id=s.campus_id join outpatient_department o on o.id=s.outpatient_department_id
      join doctor d on d.id=coalesce(s.substitute_doctor_id,s.doctor_id)
      left join clinic_type ct on ct.id=s.clinic_type_id where a.id=?
      """,appointmentId);
    if(contexts.isEmpty()) throw new PaymentException("PRICING_CONTEXT_NOT_FOUND",409);
    var c=contexts.getFirst();
    var rules=db.queryForList("""
      select * from registration_fee_rule where hospital_id=? and deleted=0 and status=1
      and (campus_id is null or campus_id=?) and (department_id is null or department_id=?)
      and (clinic_type_id is null or clinic_type_id=?) and (doctor_title_id is null or doctor_title_id=?)
      and effective_from<=? and (effective_to is null or effective_to>?)
      order by ((doctor_title_id is not null)*8+(clinic_type_id is not null)*4+
        (department_id is not null)*2+(campus_id is not null)) desc,priority desc,id asc limit 1
      """,c.get("hospital_id"),c.get("campus_id"),c.get("department_id"),c.get("clinic_type_id"),c.get("title_id"),Timestamp.from(at),Timestamp.from(at));
    if(rules.isEmpty()) {
      if(Boolean.TRUE.equals(c.get("payment_required")) || "1".equals(String.valueOf(c.get("payment_required"))))
        throw new PaymentException("PRICE_NOT_CONFIGURED",409);
      return new Quote(Money.ofCent(0,"CNY"),null,Map.of("source","COMPATIBILITY_ZERO","amountCent",0,"currency","CNY","pricedAt",at.toString()));
    }
    var r=rules.getFirst(); var snapshot=new LinkedHashMap<String,Object>();
    for(String key:new String[]{"id","rule_code","version","hospital_id","campus_id","department_id","clinic_type_id","doctor_title_id","amount_cent","currency","priority"}) snapshot.put(key,r.get(key));
    snapshot.put("pricedAt",at.toString()); snapshot.put("source","LOCAL");
    return new Quote(Money.ofCent(id(r,"amount_cent"),string(r,"currency")),id(r,"id"),snapshot);
  }
}
