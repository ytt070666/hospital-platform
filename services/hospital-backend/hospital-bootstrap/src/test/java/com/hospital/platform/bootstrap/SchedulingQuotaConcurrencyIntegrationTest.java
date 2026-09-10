package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.scheduling.SchedulingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchedulingQuotaConcurrencyIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired SchedulingService scheduling;
  @Autowired StringRedisTemplate redis;
  private final String code = "quota_" + UUID.randomUUID().toString().substring(0, 8);
  private long scheduleId;

  @Test void reservesExactlyTenOfOneHundredAndRebuildsRedisFromMysql() throws Exception {
    scheduleId = schedule(10);
    int success = reserveConcurrently(scheduleId, null, 100);
    assertThat(success).isEqualTo(10);
    assertQuota("doctor_schedule", scheduleId, 10, 10, 0);
    redis.delete("hospital:dev:schedule:quota:" + scheduleId);
    scheduling.rebuild(scheduleId);
    assertThat(redis.opsForValue().get("hospital:dev:schedule:quota:" + scheduleId)).isEqualTo("0");
    scheduling.release(scheduleId, null, 2);
    scheduling.commit(scheduleId, null, 3);
    assertQuota("doctor_schedule", scheduleId, 10, 5, 3);
  }

  @Test void reserveReleaseCommitAndInvalidMovesAlwaysPreserveMysqlInvariant() {
    scheduleId = schedule(10);
    assertThat(scheduling.reserve(scheduleId, null, 3)).isTrue();
    assertQuota("doctor_schedule", scheduleId, 10, 3, 0);
    scheduling.release(scheduleId, null, 2);
    assertQuota("doctor_schedule", scheduleId, 10, 1, 0);
    assertThat(scheduling.reserve(scheduleId, null, 2)).isTrue();
    scheduling.commit(scheduleId, null, 2);
    assertQuota("doctor_schedule", scheduleId, 10, 1, 2);
    assertThatThrownBy(() -> scheduling.release(scheduleId, null, 2)).isInstanceOf(com.hospital.platform.common.error.BusinessException.class);
    assertThatThrownBy(() -> scheduling.commit(scheduleId, null, 2)).isInstanceOf(com.hospital.platform.common.error.BusinessException.class);
    assertQuota("doctor_schedule", scheduleId, 10, 1, 2);
    redis.opsForValue().set("hospital:dev:schedule:quota:" + scheduleId, "999");
    scheduling.rebuild(scheduleId);
    assertThat(redis.opsForValue().get("hospital:dev:schedule:quota:" + scheduleId)).isEqualTo("7");
  }

  @Test void rebuildsDeletedQuotaKeyAndOnlyRestoresEffectivePublishedSchedules() {
    scheduleId = schedule(30);
    jdbc.update("update doctor_schedule set schedule_status='PUBLISHED',reserved_quota=4,booked_quota=6 where id=?", scheduleId);
    scheduling.rebuild(scheduleId);
    String activeKey = "hospital:dev:schedule:quota:" + scheduleId;
    assertThat(redis.opsForValue().get(activeKey)).isEqualTo("20");
    redis.delete(activeKey);
    assertThat(redis.hasKey(activeKey)).isFalse();
    long stopped = schedule(8, 11);
    jdbc.update("update doctor_schedule set schedule_status='STOPPED' where id=?", stopped);
    redis.opsForValue().set("hospital:dev:schedule:quota:" + stopped, "8");
    scheduling.rebuildPublishedQuotaCaches();
    assertThat(redis.opsForValue().get(activeKey)).isEqualTo("20");
    assertThat(redis.hasKey("hospital:dev:schedule:quota:" + stopped)).isFalse();
  }

  @Test void reservesExactlyThreeSlotQuotasWithTwentyConcurrentRequests() throws Exception {
    scheduleId = schedule(3);
    jdbc.update("insert into schedule_slot(schedule_id,slot_no,start_time,end_time,total_quota) values(?,?,?,?,?)", scheduleId, "S1", "08:00:00", "09:00:00", 3);
    long slot = jdbc.queryForObject("select id from schedule_slot where schedule_id=? and slot_no='S1'", Long.class, scheduleId);
    int success = reserveConcurrently(scheduleId, slot, 20);
    assertThat(success).isEqualTo(3);
    assertQuota("schedule_slot", slot, 3, 3, 0);
  }

  private long schedule(int quota) { return schedule(quota, 10); }
  private long schedule(int quota, int offsetDays) {
    jdbc.update("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,?,? ,?,'DRAFT','MANUAL',?)", code + UUID.randomUUID().toString().substring(0, 5), 900001L, 900001L, 900001L, LocalDate.now().plusDays(offsetDays), 900001L, "08:00:00", "12:00:00", quota);
    return jdbc.queryForObject("select id from doctor_schedule where schedule_no like ? order by id desc limit 1", Long.class, code + "%");
  }
  private int reserveConcurrently(long schedule, Long slot, int count) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(20); try {
      List<Callable<Boolean>> jobs = new ArrayList<>(); for (int i = 0; i < count; i++) jobs.add(() -> scheduling.reserve(schedule, slot, 1));
      List<Future<Boolean>> results = pool.invokeAll(jobs); int success = 0; for (Future<Boolean> result : results) if (result.get()) success++; return success;
    } finally { pool.shutdownNow(); }
  }
  private void assertQuota(String table, long id, int total, int reserved, int booked) {
    var row = jdbc.queryForMap("select total_quota,reserved_quota,booked_quota from " + table + " where id=?", id);
    assertThat(((Number) row.get("total_quota")).intValue()).isEqualTo(total);
    assertThat(((Number) row.get("reserved_quota")).intValue()).isEqualTo(reserved);
    assertThat(((Number) row.get("booked_quota")).intValue()).isEqualTo(booked);
    assertThat(total).isGreaterThanOrEqualTo(0); assertThat(reserved).isGreaterThanOrEqualTo(0); assertThat(booked).isGreaterThanOrEqualTo(0);
    assertThat(reserved + booked).isLessThanOrEqualTo(total); assertThat(total - reserved - booked).isGreaterThanOrEqualTo(0);
  }
  @AfterEach void clean() { List<Long> ids=jdbc.queryForList("select id from doctor_schedule where schedule_no like ?",Long.class,code+"%"); for(Long id:ids){jdbc.update("delete from schedule_slot where schedule_id=?",id);jdbc.update("delete from schedule_change_log where schedule_id=?",id);redis.delete("hospital:dev:schedule:quota:"+id);} jdbc.update("delete from doctor_schedule where schedule_no like ?",code+"%"); }
}
