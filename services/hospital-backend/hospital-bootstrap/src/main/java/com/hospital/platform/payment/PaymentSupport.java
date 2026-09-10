package com.hospital.platform.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.platform.common.trace.TraceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PaymentSupport {
  final JdbcTemplate db;
  final TransactionTemplate tx;
  final ObjectMapper json;
  public PaymentSupport(JdbcTemplate db, PlatformTransactionManager manager, ObjectMapper json) {
    this.db = db; this.json = json; this.tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }
  static String number(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }
  static Timestamp now() { return Timestamp.from(Instant.now()); }
  static long id(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
  static String string(Map<String,Object> row, String key) { return (String) row.get(key); }
  static String hash(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (Exception e) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
  }
  static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
  String json(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception e) { throw new PaymentException("PAYMENT_SERIALIZATION_FAILED", 500); }
  }
  void audit(String action, String type, Long id, String actor, Long actorId, String result, Object before, Object after) {
    db.update("insert into payment_audit_event(action,resource_type,resource_id,actor_type,actor_id,result_code,before_snapshot,after_snapshot,trace_id) values(?,?,?,?,?,?,?,?,?)",
      action,type,id,actor,actorId,result,before==null?null:json(before),after==null?null:json(after),trace());
  }
  void transaction(long id, String op, String status, String result) {
    db.update("insert into payment_transaction(payment_order_id,operation,provider_status,result_code,trace_id) values(?,?,?,?,?)",id,op,status,result,trace());
  }
  static String trace() { String t=TraceId.get(); return t==null ? number("pay-") : t; }
}
