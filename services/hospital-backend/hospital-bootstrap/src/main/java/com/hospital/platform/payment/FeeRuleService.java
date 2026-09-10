package com.hospital.platform.payment;

import static com.hospital.platform.payment.PaymentSupport.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FeeRuleService {
  private final PaymentSupport s;
  public FeeRuleService(PaymentSupport s) { this.s=s; }
  public record Input(String ruleCode,long hospitalId,Long campusId,Long departmentId,Long clinicTypeId,
      Long doctorTitleId,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=MinorAmountDeserializer.class) long amountCent,String currency,Instant effectiveFrom,Instant effectiveTo,int status,int priority,long version) {}
  public List<Map<String,Object>> list(int limit) {
    return s.db.queryForList("select id,rule_code ruleCode,hospital_id hospitalId,campus_id campusId,department_id departmentId,clinic_type_id clinicTypeId,doctor_title_id doctorTitleId,amount_cent amountCent,currency,effective_from effectiveFrom,effective_to effectiveTo,status,priority,version from registration_fee_rule where deleted=0 order by id desc limit ?",Math.clamp(limit,1,200));
  }
  public Map<String,Object> save(long actor,Long rule,Input r) {
    validate(r);
    try { return s.tx.execute(ignored->{
      Map<String,Object> before=null;
      if(rule!=null) {
        var rows=s.db.queryForList("select * from registration_fee_rule where id=? and deleted=0 for update",rule);
        if(rows.isEmpty()) throw new PaymentException("FEE_RULE_NOT_FOUND",404);
        before=rows.getFirst();
        if(id(before,"version")!=r.version()) throw new PaymentException("FEE_RULE_VERSION_CONFLICT",409);
      }
      Object[] values={r.ruleCode(),r.hospitalId(),r.campusId(),r.departmentId(),r.clinicTypeId(),r.doctorTitleId(),r.amountCent(),r.currency(),Timestamp.from(r.effectiveFrom()),r.effectiveTo()==null?null:Timestamp.from(r.effectiveTo()),r.status(),r.priority()};
      if(rule==null) s.db.update("insert into registration_fee_rule(rule_code,hospital_id,campus_id,department_id,clinic_type_id,doctor_title_id,amount_cent,currency,effective_from,effective_to,status,priority) values(?,?,?,?,?,?,?,?,?,?,?,?)",values);
      else {
        var args=new java.util.ArrayList<Object>(java.util.Arrays.asList(values));args.add(rule);args.add(r.version());
        int count=s.db.update("update registration_fee_rule set rule_code=?,hospital_id=?,campus_id=?,department_id=?,clinic_type_id=?,doctor_title_id=?,amount_cent=?,currency=?,effective_from=?,effective_to=?,status=?,priority=?,version=version+1 where id=? and version=?",args.toArray());
        if(count!=1) throw new PaymentException("FEE_RULE_VERSION_CONFLICT",409);
      }
      var after=s.db.queryForMap("select * from registration_fee_rule where rule_code=?",r.ruleCode());
      s.audit(rule==null?"FEE_RULE_CREATED":"FEE_RULE_UPDATED","FEE_RULE",id(after,"id"),"ADMIN",actor,"OK",before,after);
      return Map.of("id",id(after,"id"),"version",id(after,"version"));
    }); } catch(DuplicateKeyException e) { throw new PaymentException("FEE_RULE_CODE_CONFLICT",409); }
  }
  private void validate(Input r) {
    Money.ofCent(r.amountCent(),r.currency());
    if(r.ruleCode()==null||!r.ruleCode().matches("[A-Za-z0-9_-]{1,64}")||r.effectiveFrom()==null||
       (r.effectiveTo()!=null&&!r.effectiveTo().isAfter(r.effectiveFrom()))||(r.status()!=0&&r.status()!=1)||r.version()<0)
      throw new PaymentException("INVALID_FEE_RULE",400);
    require("select count(*) from hospital where id=? and deleted=0",r.hospitalId());
    if(r.campusId()!=null) require("select count(*) from hospital_campus where id=? and hospital_id=? and deleted=0",r.campusId(),r.hospitalId());
    if(r.departmentId()!=null) {
      require("select count(*) from department where id=? and hospital_id=? and deleted=0",r.departmentId(),r.hospitalId());
      if(r.campusId()!=null) require("select count(*) from department where id=? and campus_id=?",r.departmentId(),r.campusId());
    }
    if(r.clinicTypeId()!=null) require("select count(*) from clinic_type where id=? and deleted=0",r.clinicTypeId());
    if(r.doctorTitleId()!=null) require("select count(*) from doctor_title where id=? and deleted=0",r.doctorTitleId());
  }
  private void require(String sql,Object...args) {
    if(s.db.queryForObject(sql,Long.class,args)!=1) throw new PaymentException("INVALID_FEE_REFERENCE",400);
  }
}
