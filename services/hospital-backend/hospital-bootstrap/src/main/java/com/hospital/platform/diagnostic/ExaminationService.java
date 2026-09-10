package com.hospital.platform.diagnostic;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Examination execution remains in the shared clinical-order model and is always SQL scope-bound. */
@Service public class ExaminationService {
  private final JdbcTemplate jdbc; private final String releasePolicy;
  public ExaminationService(JdbcTemplate jdbc,@Value("${hospital.clinical.diagnostic.patient-release-policy:IMMEDIATE_AFTER_FINAL}") String releasePolicy){this.jdbc=jdbc;this.releasePolicy=Set.of("IMMEDIATE_AFTER_FINAL","AFTER_DOCTOR_REVIEW","MANUAL_RELEASE").contains(releasePolicy)?releasePolicy:"MANUAL_RELEASE";}
  public List<Order> orders(long user){return jdbc.queryForList("select o.* from clinical_order o join clinical_execution_user_scope s on s.user_id=? and s.department_id=o.department_id and s.service_type='EXAMINATION' where o.order_type='EXAMINATION' order by o.id desc",user).stream().map(this::order).toList();}
  @Transactional public Order accept(long user,long id,Version v){return transition(user,id,v,"PLACED","ACCEPTED","EXAMINATION_ACCEPT");}
  @Transactional public Order start(long user,long id,Version v){return transition(user,id,v,"ACCEPTED","IN_PROGRESS","EXAMINATION_START");}
  @Transactional public Order complete(long user,long id,Version v){return transition(user,id,v,"IN_PROGRESS","COMPLETED","EXAMINATION_COMPLETE");}
  @Transactional public Report createReport(long user,long orderId,Input in){
    Map<String,Object> o=lockedOrder(user,orderId); if(!"COMPLETED".equals(text(o,"status"))||blank(in.findings()))throw new BusinessException(ErrorCode.DIAGNOSTIC_INVALID_STATE);
    String no="DR"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase();
    jdbc.update("insert into diagnostic_report(report_no,clinical_order_id,patient_id,encounter_id,report_type,release_policy,status) values(?,?,?,?,? ,?,'DRAFT')",no,orderId,num(o,"patient_id"),num(o,"encounter_id"),"EXAMINATION",releasePolicy);
    long report=lastId(); jdbc.update("insert into diagnostic_report_revision(report_id,revision_no,revision_type,findings,impression,conclusion_text) values(?,1,'INITIAL',?,?,?)",report,clean(in.findings(),20000),clean(in.impression(),10000),clean(in.conclusion(),10000)); long revision=lastId();
    jdbc.update("update diagnostic_report set current_revision_id=? where id=?",revision,report); audit(user,"EXAMINATION_REPORT_CREATE","DIAGNOSTIC_REPORT",report); return new Report(report,0);
  }
  private Order transition(long user,long id,Version v,String from,String to,String audit){Map<String,Object> o=lockedOrder(user,id);if(v==null||num(o,"version")!=v.version()||!from.equals(text(o,"status")))throw new BusinessException(ErrorCode.DIAGNOSTIC_INVALID_STATE);String timing="";if("ACCEPTED".equals(to))timing=",accepted_at=current_timestamp(3)";if("IN_PROGRESS".equals(to))timing=",started_at=current_timestamp(3)";if(jdbc.update("update clinical_order set status=?,version=version+1"+timing+" where id=? and status=? and version=?",to,id,from,v.version())!=1)throw new BusinessException(ErrorCode.DIAGNOSTIC_VERSION_CONFLICT);audit(user,audit,"CLINICAL_ORDER",id);return order(one("select * from clinical_order where id=?",id));}
  private Map<String,Object> lockedOrder(long user,long id){Map<String,Object> o=one("select * from clinical_order where id=? for update",id);if(!"EXAMINATION".equals(text(o,"order_type"))||jdbc.queryForObject("select count(*) from clinical_execution_user_scope where user_id=? and department_id=? and service_type='EXAMINATION'",Long.class,user,num(o,"department_id"))!=1)throw new BusinessException(ErrorCode.DIAGNOSTIC_ACCESS_DENIED);return o;}
  private void audit(long user,String action,String type,long id){jdbc.update("insert into clinical_diagnostic_audit(actor_id,action,resource_type,resource_id,trace_id) values(?,?,?,?,?)",user,action,type,id,TraceId.get());}
  private Map<String,Object> one(String sql,Object... args){return jdbc.queryForList(sql,args).stream().findFirst().orElseThrow(()->new BusinessException(ErrorCode.DIAGNOSTIC_ACCESS_DENIED));} private long lastId(){return jdbc.queryForObject("select last_insert_id()",Long.class);} private Order order(Map<String,Object> o){return new Order(num(o,"id"),text(o,"order_no"),text(o,"status"),num(o,"version"));} private static long num(Map<String,Object>x,String k){return ((Number)x.get(k)).longValue();} private static String text(Map<String,Object>x,String k){return x.get(k)==null?null:String.valueOf(x.get(k));} private static boolean blank(String s){return s==null||s.trim().isEmpty();} private static String clean(String s,int max){return s==null?null:s.trim().substring(0,Math.min(s.trim().length(),max));}
  public record Order(long id,String orderNo,String status,long version){} public record Version(long version){} public record Input(String findings,String impression,String conclusion){} public record Report(long id,long version){}
}
