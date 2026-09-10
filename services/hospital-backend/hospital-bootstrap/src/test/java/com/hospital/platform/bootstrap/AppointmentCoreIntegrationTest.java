package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.appointment.AppointmentService;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
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

/** Exercises the MySQL state machine with real concurrent service calls, not mocked inventory. */
@SpringBootTest
class AppointmentCoreIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired AppointmentService appointments;
  @Autowired SchedulingService scheduling;
  @Autowired StringRedisTemplate redis;
  private final String prefix="ap_"+UUID.randomUUID().toString().substring(0,8);

  @Test void holdReplayConfirmCancelAndOwnershipPreserveQuotaExactlyOnce() {
    long schedule=schedule(3,false), patient=patient(), member=member(patient);
    var hold=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"key-a","WEB_PATIENT");
    var replay=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"key-a","WEB_PATIENT");
    assertThat(replay.id()).isEqualTo(hold.id()); quota(schedule,null,3,1,0);
    long differentSchedule=schedule(3,false);assertThatThrownBy(()->appointments.hold(patient,new AppointmentService.HoldRequest(member,differentSchedule,null),"key-a","WEB_PATIENT")).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).errorCode()).isEqualTo(ErrorCode.APPOINTMENT_IDEMPOTENCY_CONFLICT);
    long other=patient(); assertThatThrownBy(()->appointments.detail(other,hold.id())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).errorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_OWNED);
    assertThat(appointments.confirm(patient,hold.id()).status()).isEqualTo("BOOKED"); quota(schedule,null,3,0,1);
    assertThat(appointments.confirm(patient,hold.id()).status()).isEqualTo("BOOKED"); quota(schedule,null,3,0,1);
    assertThat(appointments.cancel(patient,hold.id(),"PATIENT","changed").status()).isEqualTo("CANCELLED"); quota(schedule,null,3,0,0);
    assertThat(appointments.cancel(patient,hold.id(),"PATIENT","changed").status()).isEqualTo("CANCELLED"); quota(schedule,null,3,0,0);
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=?",Long.class,hold.id())).isEqualTo(3);
  }

  @Test void validatesSlotAndExpiredHoldCannotBeConfirmed() {
    long schedule=schedule(3,true), patient=patient(), member=member(patient), slot=slot(schedule,3);
    assertThatThrownBy(()->appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"no-slot","WEB_PATIENT")).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).errorCode()).isEqualTo(ErrorCode.SLOT_NOT_BOOKABLE);
    var hold=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,slot),"slot","WEB_PATIENT"); quota(schedule,slot,3,1,0);
    jdbc.update("update appointment set hold_expires_at=date_sub(current_timestamp(3),interval 1 second) where id=?",hold.id());
    assertThatThrownBy(()->appointments.confirm(patient,hold.id())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).errorCode()).isEqualTo(ErrorCode.APPOINTMENT_EXPIRED);
    assertThat(appointments.detail(patient,hold.id()).status()).isEqualTo("EXPIRED"); quota(schedule,slot,3,0,0);
  }

  @Test void oneThousandConcurrentPatientsCanOnlyHoldTenQuotas() throws Exception {
    long schedule=schedule(10,false); List<Long> patients=new ArrayList<>(); List<Long> members=new ArrayList<>();
    for(int i=0;i<1000;i++){long patient=patient();patients.add(patient);members.add(member(patient));}
    ExecutorService pool=Executors.newFixedThreadPool(64); try {
      List<Callable<Boolean>> jobs=new ArrayList<>(); for(int i=0;i<1000;i++){long patient=patients.get(i),member=members.get(i);jobs.add(()->{try{appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),UUID.randomUUID().toString(),"WEB_PATIENT");return true;}catch(BusinessException e){return false;}});}
      int success=0;for(Future<Boolean> future:pool.invokeAll(jobs))if(future.get())success++;
      assertThat(success).isEqualTo(10); assertThat(jdbc.queryForObject("select count(*) from appointment where schedule_id=? and status='HOLDING'",Long.class,schedule)).isEqualTo(10);quota(schedule,null,10,10,0);
    } finally {pool.shutdownNow();}
  }

  @Test void stoppingScheduleCancelsOneHundredTwentyActiveAppointmentsInBatchesExactlyOnce() {
    long schedule=schedule(120,false);List<Long> patients=new ArrayList<>(),appointmentsToStop=new ArrayList<>();
    for(int i=0;i<120;i++){long patient=patient(),member=member(patient);patients.add(patient);var hold=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"stop-"+i,"WEB_PATIENT");appointmentsToStop.add(hold.id());if(i%2==0)appointments.confirm(patient,hold.id());}
    scheduling.stop(schedule,"SCHEDULE_STOPPED","批量停诊验收",180,9988L);
    assertThat(jdbc.queryForObject("select count(*) from appointment where schedule_id=? and status='CANCELLED'",Long.class,schedule)).isEqualTo(120);
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id in (select id from appointment where schedule_id=?) and to_status='CANCELLED' and reason_code='SCHEDULE_STOPPED'",Long.class,schedule)).isEqualTo(120);
    quota(schedule,null,120,0,0);
    for(int i=0;i<3;i++)appointments.cancelForStoppedSchedule(schedule,9988L);
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id in (select id from appointment where schedule_id=?) and to_status='CANCELLED' and reason_code='SCHEDULE_STOPPED'",Long.class,schedule)).isEqualTo(120);
  }

  @Test void concurrentSameKeyAndSameMemberOnlyReserveOnce() throws Exception {
    long schedule=schedule(20,false),patient=patient(),member=member(patient); ExecutorService pool=Executors.newFixedThreadPool(32);try {
      List<Callable<Long>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(()->appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"same-key","WEB_PATIENT").id());
      List<Future<Long>> result=pool.invokeAll(jobs);long id=result.getFirst().get();for(Future<Long> item:result)assertThat(item.get()).isEqualTo(id);quota(schedule,null,20,1,0);
    }finally{pool.shutdownNow();}
    long secondSchedule=schedule(20,false),secondPatient=patient(),secondMember=member(secondPatient);ExecutorService duplicates=Executors.newFixedThreadPool(32);try {
      List<Callable<Boolean>> jobs=new ArrayList<>();for(int i=0;i<100;i++){String key="key-"+i;jobs.add(()->{try{appointments.hold(secondPatient,new AppointmentService.HoldRequest(secondMember,secondSchedule,null),key,"WEB_PATIENT");return true;}catch(BusinessException e){return false;}});}int success=0;for(Future<Boolean> item:duplicates.invokeAll(jobs))if(item.get())success++;assertThat(success).isEqualTo(1);quota(secondSchedule,null,20,1,0);
    }finally{duplicates.shutdownNow();}
  }

  @Test void concurrentConfirmAndCancelApplyOnlyOneInventoryMove() throws Exception {
    long schedule=schedule(2,false),patient=patient(),member=member(patient);var hold=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"race","WEB_PATIENT");
    ExecutorService pool=Executors.newFixedThreadPool(20);try{List<Callable<Void>> jobs=new ArrayList<>();for(int i=0;i<50;i++)jobs.add(()->{try{appointments.confirm(patient,hold.id());}catch(BusinessException ignored){}return null;});for(int i=0;i<50;i++)jobs.add(()->{try{appointments.cancel(patient,hold.id(),"RACE",null);}catch(BusinessException ignored){}return null;});for(Future<Void> result:pool.invokeAll(jobs))result.get();}finally{pool.shutdownNow();}
    String status=appointments.detail(patient,hold.id()).status();assertThat(status).isIn("BOOKED","CANCELLED");if("BOOKED".equals(status))quota(schedule,null,2,0,1);else quota(schedule,null,2,0,0);
  }

  @Test void oneHundredConcurrentConfirmsCommitExactlyOnceAndOneHundredCancelsReleaseExactlyOnce() throws Exception {
    long schedule=schedule(2,false),patient=patient(),member=member(patient);var hold=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"confirm-100","WEB_PATIENT");
    concurrent(100,()->{appointments.confirm(patient,hold.id());return null;});assertThat(appointments.detail(patient,hold.id()).status()).isEqualTo("BOOKED");quota(schedule,null,2,0,1);
    concurrent(100,()->{appointments.cancel(patient,hold.id(),"LOAD",null);return null;});assertThat(appointments.detail(patient,hold.id()).status()).isEqualTo("CANCELLED");quota(schedule,null,2,0,0);
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=? and to_status='BOOKED'",Long.class,hold.id())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=? and to_status='CANCELLED'",Long.class,hold.id())).isEqualTo(1);
  }

  @Test void tenExpirationWorkersAndConfirmCancelRacesReleaseAtMostOnce() throws Exception {
    long schedule=schedule(3,false),patient=patient(),member=member(patient);var expired=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"expire-workers","WEB_PATIENT");jdbc.update("update appointment set hold_expires_at=date_sub(current_timestamp(3),interval 1 second) where id=?",expired.id());
    concurrent(10,()->{appointments.expireDue();return null;});assertThat(appointments.detail(patient,expired.id()).status()).isEqualTo("EXPIRED");quota(schedule,null,3,0,0);assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=? and to_status='EXPIRED'",Long.class,expired.id())).isEqualTo(1);
    var confirmRace=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"confirm-expire","WEB_PATIENT");jdbc.update("update appointment set hold_expires_at=date_sub(current_timestamp(3),interval 1 second) where id=?",confirmRace.id());concurrent(2,()->{try{appointments.confirm(patient,confirmRace.id());}catch(BusinessException ignored){}appointments.expireDue();return null;});assertThat(appointments.detail(patient,confirmRace.id()).status()).isEqualTo("EXPIRED");quota(schedule,null,3,0,0);
    var cancelRace=appointments.hold(patient,new AppointmentService.HoldRequest(member,schedule,null),"cancel-expire","WEB_PATIENT");jdbc.update("update appointment set hold_expires_at=date_sub(current_timestamp(3),interval 1 second) where id=?",cancelRace.id());concurrent(2,()->{try{appointments.cancel(patient,cancelRace.id(),"RACE",null);}catch(BusinessException ignored){}appointments.expireDue();return null;});assertThat(appointments.detail(patient,cancelRace.id()).status()).isIn("CANCELLED","EXPIRED");quota(schedule,null,3,0,0);
  }

  @Test void oneHundredSlotHoldsAndRedisLossWrongValueRebuildUseMysqlTruth() throws Exception {
    long schedule=schedule(3,true),slot=slot(schedule,3);List<Long> patients=new ArrayList<>(),members=new ArrayList<>();for(int i=0;i<100;i++){long p=patient();patients.add(p);members.add(member(p));}
    ExecutorService pool=Executors.newFixedThreadPool(32);try{List<Callable<Boolean>> jobs=new ArrayList<>();for(int i=0;i<100;i++){long p=patients.get(i),m=members.get(i);jobs.add(()->{try{appointments.hold(p,new AppointmentService.HoldRequest(m,schedule,slot),UUID.randomUUID().toString(),"WEB_PATIENT");return true;}catch(BusinessException e){return false;}});}int success=0;for(Future<Boolean> f:pool.invokeAll(jobs))if(f.get())success++;assertThat(success).isEqualTo(3);}finally{pool.shutdownNow();}
    quota(schedule,slot,3,3,0);assertThat(jdbc.queryForObject("select count(*) from appointment where schedule_id=? and slot_id=? and status='HOLDING'",Long.class,schedule,slot)).isEqualTo(3);String key="hospital:dev:schedule:slot:quota:"+slot;assertThat(redis.opsForValue().get(key)).isEqualTo("0");redis.delete(key);assertThat(redis.hasKey(key)).isFalse();scheduling.rebuild(schedule);assertThat(redis.opsForValue().get(key)).isEqualTo("0");redis.opsForValue().set(key,"999");scheduling.rebuild(schedule);assertThat(redis.opsForValue().get(key)).isEqualTo("0");
  }

  private long patient(){String no=prefix+"p"+UUID.randomUUID().toString().substring(0,12);jdbc.update("insert into patient(patient_no,name_ciphertext,status) values(?, ?,1)",no,"test");return jdbc.queryForObject("select id from patient where patient_no=?",Long.class,no);}
  private long member(long patient){String no=prefix+"m"+UUID.randomUUID().toString().substring(0,12);jdbc.update("insert into patient_member(patient_id,member_no,relationship_code,name_encrypted,status) values(?,?, 'CHILD',?,'ACTIVE')",patient,no,"test");return jdbc.queryForObject("select id from patient_member where member_no=?",Long.class,no);}
  private long schedule(int quota,boolean slotMode){String no=prefix+"s"+UUID.randomUUID().toString().substring(0,12);long doctor=1_000_000L+(UUID.randomUUID().getLeastSignificantBits()&0x3fffffffL);jdbc.update("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota,slot_mode) values(?,?,?,?,?,?,?,?,'PUBLISHED','MANUAL',?,?)",no,doctor,900001L,900001L,LocalDate.now().plusDays(10),900001L,"08:00:00","12:00:00",quota,slotMode?1:0);return jdbc.queryForObject("select id from doctor_schedule where schedule_no=?",Long.class,no);}
  private long slot(long schedule,int quota){jdbc.update("insert into schedule_slot(schedule_id,slot_no,start_time,end_time,total_quota,status) values(?,?, '08:00:00','09:00:00',?,'ACTIVE')",schedule,prefix+"slot",quota);return jdbc.queryForObject("select id from schedule_slot where schedule_id=?",Long.class,schedule);}
  private void quota(long schedule,Long slot,int total,int reserved,int booked){String table=slot==null?"doctor_schedule":"schedule_slot";long id=slot==null?schedule:slot;var row=jdbc.queryForMap("select total_quota,reserved_quota,booked_quota from "+table+" where id=?",id);assertThat(((Number)row.get("total_quota")).intValue()).isEqualTo(total);assertThat(((Number)row.get("reserved_quota")).intValue()).isEqualTo(reserved);assertThat(((Number)row.get("booked_quota")).intValue()).isEqualTo(booked);}
  private void concurrent(int count,Callable<Void> action) throws Exception {ExecutorService pool=Executors.newFixedThreadPool(Math.min(32,count));try{List<Callable<Void>> jobs=new ArrayList<>();for(int i=0;i<count;i++)jobs.add(action);for(Future<Void> future:pool.invokeAll(jobs))future.get();}finally{pool.shutdownNow();}}
  @AfterEach void cleanup(){jdbc.update("delete h from appointment_status_history h join appointment a on a.id=h.appointment_id join doctor_schedule s on s.id=a.schedule_id where s.schedule_no like ?",prefix+"%");jdbc.update("delete from appointment where schedule_id in (select id from doctor_schedule where schedule_no like ?)",prefix+"%");jdbc.update("delete from schedule_slot where schedule_id in (select id from doctor_schedule where schedule_no like ?)",prefix+"%");jdbc.update("delete from doctor_schedule where schedule_no like ?",prefix+"%");jdbc.update("delete from patient_member where member_no like ?",prefix+"%");jdbc.update("delete from patient where patient_no like ?",prefix+"%");}
}
