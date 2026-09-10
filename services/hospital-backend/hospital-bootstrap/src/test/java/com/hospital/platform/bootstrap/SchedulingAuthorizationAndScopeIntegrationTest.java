package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.masterdata.MasterDataService;
import com.hospital.platform.appointment.AppointmentService;
import com.hospital.platform.appointment.AppointmentInventoryReconciliationService;
import com.hospital.platform.patient.PatientCrypto;
import com.hospital.platform.scheduling.SchedulingService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SchedulingAuthorizationAndScopeIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired MasterDataService master;
  @Autowired SchedulingService scheduling;
  @Autowired AppointmentService appointments;
  @Autowired AppointmentInventoryReconciliationService reconciliation;
  @Autowired PatientCrypto crypto;
  @Autowired ObjectMapper json;
  private final String prefix = "schedule_scope_" + UUID.randomUUID().toString().substring(0, 8);
  private long hospital, campus, organization, adminA, adminB, adminC, departmentA, departmentB, departmentC, outpatientA, outpatientB, outpatientC, doctorA, doctorB, doctorC, session, actor, role;

  @Test void scheduleViewerCanListButCannotCallAnyMutationEndpoint() throws Exception {
    var user = new AuthenticatedUser(998877L, "schedule_viewer", 0, "ADMIN", Set.of("hospital:schedule:list"));
    var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("hospital:schedule:list")));
    mvc.perform(get("/api/v1/admin/schedules").param("page", "1").param("pageSize", "20").with(authentication(auth))).andExpect(status().isOk());
    for (String path : List.of("/api/v1/admin/schedules/1/publish", "/api/v1/admin/schedules/1/stop", "/api/v1/admin/schedules/1/substitute", "/api/v1/admin/schedules/1/quota", "/api/v1/admin/schedules/1/slots/generate")) {
      mvc.perform(post(path).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"权限验收\",\"reasonCode\":\"TEST\",\"doctorId\":1,\"totalQuota\":1,\"durationMinutes\":30,\"quota\":1}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(post("/api/v1/admin/schedules").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test void scheduleQueriesRespectCustomAdministrativeDepartmentScopeEvenWithFilters() {
    createScopeGraph();
    var allVisible = scheduling.schedules(actor, new SchedulingService.ScheduleFilter(null, null, null, null, LocalDate.now(), LocalDate.now().plusDays(7), 1, 20), false);
    assertThat(allVisible.records()).extracting(row -> ((Number) row.get("departmentId")).longValue()).containsExactlyInAnyOrder(departmentA, departmentB);
    var blocked = scheduling.schedules(actor, new SchedulingService.ScheduleFilter(null, departmentC, null, null, LocalDate.now(), LocalDate.now().plusDays(7), 1, 20), false);
    assertThat(blocked.records()).isEmpty();
  }

  @Test void templateGenerationIsIdempotentAndWorkflowWritesScheduleChangeAudit() {
    createScopeGraph();
    LocalDate date = LocalDate.now().plusDays(6);
    long template = scheduling.createTemplate(new SchedulingService.TemplateRequest(prefix + "template", "排班工作流模板", doctorA, campus, outpatientA, null, LocalDate.now(), LocalDate.now().plusDays(20), 10, null, null, true, true, List.of(new SchedulingService.TemplateRule(date.getDayOfWeek().getValue(), session, 0)), 0), actor);
    List<Long> first = scheduling.generateTemplate(template, date, date, actor);
    assertThat(first).hasSize(1);
    assertThat(scheduling.generateTemplate(template, date, date, actor)).isEmpty();
    assertThatThrownBy(() -> scheduling.createSchedule(new SchedulingService.ScheduleRequest(null, doctorA, campus, outpatientA, null, date, session, LocalTime.of(8, 30), LocalTime.of(9, 30), "MANUAL", null, 10, false, 0), actor))
        .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.SCHEDULE_CONFLICT);
    long schedule = first.getFirst();
    scheduling.publish(schedule, 0, actor);
    scheduling.substitute(schedule, doctorB, "排班验收替诊", 1, actor);
    scheduling.adjustQuota(schedule, 16, "排班验收加号", 2, actor);
    scheduling.generateSlots(schedule, 30, 8, actor);
    scheduling.stop(schedule, "TEST", "排班验收停诊", 4, actor);
    assertThat(scheduling.changes(schedule)).extracting(row -> row.get("action"))
        .contains("CREATE", "PUBLISH", "SUBSTITUTE", "ADD_QUOTA", "SLOT_CHANGE", "STOP");
    assertThat(scheduling.changes(schedule)).allSatisfy(row -> assertThat(row.get("traceId")).isNotNull());
  }

  @Test void appointmentAdministrationUsesSqlScopeMaskedDtoPaginationAndIdempotentStaffCancel() throws Exception {
    createScopeGraph();
    long scheduleA = id("select id from doctor_schedule where schedule_no=?", prefix + "noA");
    long scheduleB = id("select id from doctor_schedule where schedule_no=?", prefix + "noB");
    long scheduleC = id("select id from doctor_schedule where schedule_no=?", prefix + "noC");
    scheduling.publish(scheduleA, 0, actor); scheduling.publish(scheduleB, 0, actor); scheduling.publish(scheduleC, 0, actor);
    long patientA = patient("A"), memberA = member(patientA, "预约验收甲");
    long patientB = patient("B"), memberB = member(patientB, "预约验收乙");
    long patientC = patient("C"), memberC = member(patientC, "预约验收丙");
    var appointmentA = appointments.hold(patientA, new AppointmentService.HoldRequest(memberA, scheduleA, null), prefix + "keyA", "WEB_PATIENT");
    appointments.hold(patientB, new AppointmentService.HoldRequest(memberB, scheduleB, null), prefix + "keyB", "WEB_PATIENT");
    var hidden = appointments.hold(patientC, new AppointmentService.HoldRequest(memberC, scheduleC, null), prefix + "keyC", "WEB_PATIENT");

    var page = appointments.adminList(actor, new AppointmentService.AdminQuery(null, null, "预约验收甲", null, null, null, null, null, "HOLDING", null, null, 1, 1));
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records()).singleElement().satisfies(row -> assertThat(row.memberName()).isEqualTo("预*"));
    assertThat(json.writeValueAsString(page.records().getFirst())).doesNotContain("nameEncrypted", "nameHash", "patientId", "idNumber", "mobile", "token");
    assertThat(appointments.list(patientA, "HOLDING", 1, 1).records()).extracting(AppointmentService.View::id).containsExactly(appointmentA.id());
    assertThatThrownBy(() -> appointments.adminDetail(actor, hidden.id())).isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    var detail = appointments.adminDetail(actor, appointmentA.id());
    assertThat(detail.inventory()).containsKey("schedule");
    assertThat(detail.statusHistory()).extracting(row -> row.get("toStatus")).contains("HOLDING");
    assertThat(appointments.cancelByStaff(actor, appointmentA.id(), "STAFF_CANCEL", "预约验收协助取消").status()).isEqualTo("CANCELLED");
    assertThat(appointments.cancelByStaff(actor, appointmentA.id(), "STAFF_CANCEL", "重复点击").status()).isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=? and to_status='CANCELLED'", Long.class, appointmentA.id())).isEqualTo(1);

    var viewer = new AuthenticatedUser(actor, "appointment_viewer", 0, "ADMIN", Set.of("hospital:appointment:list", "hospital:appointment:view"));
    var viewerAuth = new UsernamePasswordAuthenticationToken(viewer, null, List.of(new SimpleGrantedAuthority("hospital:appointment:list"), new SimpleGrantedAuthority("hospital:appointment:view")));
    mvc.perform(post("/api/v1/admin/appointments/{id}/cancel", appointmentA.id()).with(authentication(viewerAuth)).contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"STAFF_CANCEL\",\"reason\":\"禁止\"}")).andExpect(status().isForbidden());
  }

  @Test void stoppedScheduleCancelsHoldingAndBookedExactlyOnceAndBlocksUnpublish() {
    createScopeGraph();
    long schedule = id("select id from doctor_schedule where schedule_no=?", prefix + "noA");
    scheduling.publish(schedule, 0, actor);
    long holdingPatient = patient("stopH"), holdingMember = member(holdingPatient, "停诊候诊人");
    long bookedPatient = patient("stopB"), bookedMember = member(bookedPatient, "停诊已约人");
    var holding = appointments.hold(holdingPatient, new AppointmentService.HoldRequest(holdingMember, schedule, null), prefix + "stop-h", "WEB_PATIENT");
    var booked = appointments.hold(bookedPatient, new AppointmentService.HoldRequest(bookedMember, schedule, null), prefix + "stop-b", "WEB_PATIENT");
    appointments.confirm(bookedPatient, booked.id());
    assertThatThrownBy(() -> scheduling.unpublish(schedule, 1, actor)).isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.SCHEDULE_HAS_ACTIVE_APPOINTMENTS);
    scheduling.stop(schedule, "SCHEDULE_STOPPED", "停诊专项验收", 4, actor);
    assertThat(appointments.detail(holdingPatient, holding.id()).status()).isEqualTo("CANCELLED");
    assertThat(appointments.detail(bookedPatient, booked.id()).status()).isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id in (?,?) and to_status='CANCELLED' and reason_code='SCHEDULE_STOPPED'", Long.class, holding.id(), booked.id())).isEqualTo(2);
    assertThat(jdbc.queryForObject("select reserved_quota+booked_quota from doctor_schedule where id=?", Long.class, schedule)).isZero();
    assertThatThrownBy(() -> appointments.hold(holdingPatient, new AppointmentService.HoldRequest(holdingMember, schedule, null), prefix + "stopped", "WEB_PATIENT"))
        .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.SCHEDULE_NOT_BOOKABLE);
  }

  @Test void substituteDeadlineSlotProtectionAndReadOnlyReconciliationPreserveBookingTruth() {
    createScopeGraph();
    long schedule = id("select id from doctor_schedule where schedule_no=?", prefix + "noA");
    long conflicting = id("select id from doctor_schedule where schedule_no=?", prefix + "noB");
    scheduling.publish(schedule, 0, actor);
    scheduling.stop(conflicting, "TEST", "释放替诊医生", 0, actor);
    long patient = patient("sub"), member = member(patient, "替诊预约人");
    var appointment = appointments.hold(patient, new AppointmentService.HoldRequest(member, schedule, null), prefix + "sub", "WEB_PATIENT");
    appointments.confirm(patient, appointment.id());
    scheduling.substitute(schedule, doctorB, "原医生临时停诊", 3, actor);
    var detail = appointments.detail(patient, appointment.id());
    assertThat(detail.status()).isEqualTo("BOOKED");
    assertThat(detail.substituted()).isTrue();
    assertThat(detail.originalDoctorName()).isEqualTo("医生A");
    assertThat(detail.doctorName()).isEqualTo("医生B");
    assertThatThrownBy(() -> scheduling.generateSlots(schedule, 30, 5, actor)).isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.SLOT_HAS_ACTIVE_APPOINTMENTS);
    assertThat(reconciliation.inspectSchedule(schedule)).isEmpty();
    jdbc.update("update doctor_schedule set booked_quota=0 where id=?", schedule);
    assertThat(reconciliation.inspectSchedule(schedule)).singleElement().satisfies(mismatch -> {
      assertThat(mismatch.scope()).isEqualTo("SCHEDULE");
      assertThat(mismatch.bookedQuota()).isZero();
      assertThat(mismatch.bookedCount()).isOne();
    });
    jdbc.update("update doctor_schedule set schedule_date=current_date(),start_time=addtime(current_time(),'00:10:00'),end_time=addtime(current_time(),'00:40:00'),booked_quota=1 where id=?", schedule);
    assertThatThrownBy(() -> appointments.cancel(patient, appointment.id(), "PATIENT", "过期取消窗口")).isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.APPOINTMENT_CANCEL_DEADLINE_PASSED);
    assertThat(appointments.cancelByStaff(actor, appointment.id(), "STAFF_CANCEL", "人工协助取消").status()).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> scheduling.generateSlots(schedule, 30, 5, actor)).isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(ErrorCode.SLOT_HAS_APPOINTMENT_HISTORY);
  }

  private void createScopeGraph() {
    hospital = master.createHospital(new MasterDataService.HospitalRequest(prefix + "h", "排班范围医院", null, null, "UNKNOWN", null, "排班范围验收", null, null, null, null, null, null, null, null, true, 0, true, 0), 1);
    campus = master.createCampus(new MasterDataService.CampusRequest(hospital, prefix + "c", "排班范围院区", null, null, null, null, null, null, null, null, true, 0, true, 0));
    jdbc.update("insert into sys_organization(org_code,org_name,org_type) values(?,?,?)", prefix + "o", "排班范围组织", "TEST");
    organization = id("select id from sys_organization where org_code=?", prefix + "o");
    adminA = administrative("A"); adminB = administrative("B"); adminC = administrative("C");
    departmentA = department(adminA, "A"); departmentB = department(adminB, "B"); departmentC = department(adminC, "C");
    outpatientA = outpatient(departmentA, "A"); outpatientB = outpatient(departmentB, "B"); outpatientC = outpatient(departmentC, "C");
    doctorA = doctor(departmentA, "A"); doctorB = doctor(departmentB, "B"); doctorC = doctor(departmentC, "C");
    session = scheduling.createSession(new SchedulingService.SessionRequest(hospital, prefix + "s", "范围测试时段", LocalTime.of(8, 0), LocalTime.of(9, 0), 0, true, 0));
    schedule(doctorA, outpatientA, "A"); schedule(doctorB, outpatientB, "B"); schedule(doctorC, outpatientC, "C");
    jdbc.update("insert into sys_user(username,password_hash,display_name) values(?,?,?)", prefix + "u", "x", "排班自定义范围用户");
    actor = id("select id from sys_user where username=?", prefix + "u");
    jdbc.update("insert into sys_role(role_code,role_name,data_scope) values(?,?,?)", prefix + "r", "排班自定义范围角色", "CUSTOM");
    role = id("select id from sys_role where role_code=?", prefix + "r");
    jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", actor, role);
    jdbc.update("insert into sys_role_data_scope(role_id,target_type,target_id) values(?,?,?),(?,?,?)", role, "DEPARTMENT", adminA, role, "DEPARTMENT", adminB);
  }

  private long administrative(String suffix) { jdbc.update("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,?)", organization, prefix + "admin" + suffix, "行政" + suffix, "TEST"); return id("select id from sys_department where organization_id=? and dept_code=?", organization, prefix + "admin" + suffix); }
  private long department(long administrative, String suffix) { return master.createDepartment(new MasterDataService.DepartmentRequest(organization, administrative, hospital, campus, null, prefix + "dept" + suffix, "临床" + suffix, null, "CLINICAL", "范围测试", null, null, null, null, null, null, true, 0, true, 0)); }
  private long outpatient(long department, String suffix) { return master.createOutpatientDepartment(new MasterDataService.OutpatientDepartmentRequest(department, null, prefix + "out" + suffix, "门诊" + suffix, null, true, 0, true, 0)); }
  private long doctor(long department, String suffix) { return master.createDoctor(new MasterDataService.DoctorRequest(prefix + "doc" + suffix, null, "医生" + suffix, null, null, null, "范围测试", null, null, null, null, true, 0, true, List.of(department), department, List.of(), 0)); }
  private void schedule(long doctor, long outpatient, String suffix) { jdbc.update("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,?,?,?,'DRAFT','MANUAL',?)", prefix + "no" + suffix, doctor, campus, outpatient, LocalDate.now().plusDays(2), session, "08:00:00", "09:00:00", 10); }
  private long patient(String suffix) { String no = prefix + "patient" + suffix; jdbc.update("insert into patient(patient_no,name_ciphertext,status) values(?,?,1)", no, crypto.encrypt("预约患者" + suffix)); return id("select id from patient where patient_no=?", no); }
  private long member(long patient, String name) { String no = prefix + "member" + UUID.randomUUID().toString().substring(0, 6); jdbc.update("insert into patient_member(patient_id,member_no,relationship_code,name_encrypted,name_hash,status) values(?,?,?,?,?,'ACTIVE')", patient, no, "SELF", crypto.encrypt(name), crypto.hmac(name)); return id("select id from patient_member where member_no=?", no); }
  private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

  @AfterEach void clean() {
    jdbc.update("delete h from appointment_status_history h join appointment a on a.id=h.appointment_id join patient p on p.id=a.patient_id where p.patient_no like ?", prefix + "%");
    jdbc.update("delete from appointment where patient_id in (select id from patient where patient_no like ?)", prefix + "%");
    jdbc.update("delete from patient_member where member_no like ?", prefix + "%");
    jdbc.update("delete from patient where patient_no like ?", prefix + "%");
    jdbc.update("delete from schedule_slot where schedule_id in (select id from doctor_schedule where schedule_no like ? or template_id in (select id from schedule_template where template_code like ?))", prefix + "%", prefix + "%");
    jdbc.update("delete from schedule_change_log where schedule_id in (select id from doctor_schedule where schedule_no like ? or template_id in (select id from schedule_template where template_code like ?))", prefix + "%", prefix + "%");
    jdbc.update("delete from doctor_schedule where schedule_no like ? or template_id in (select id from schedule_template where template_code like ?)", prefix + "%", prefix + "%");
    jdbc.update("delete r from schedule_template_rule r join schedule_template t on t.id=r.template_id where t.template_code like ?", prefix + "%");
    jdbc.update("delete from schedule_template where template_code like ?", prefix + "%");
    jdbc.update("delete from schedule_session_definition where code like ?", prefix + "%");
    jdbc.update("delete ds from sys_role_data_scope ds join sys_role r on r.id=ds.role_id where r.role_code like ?", prefix + "%");
    jdbc.update("delete ur from sys_user_role ur join sys_user u on u.id=ur.user_id where u.username like ?", prefix + "%");
    jdbc.update("delete from sys_user where username like ?", prefix + "%");
    jdbc.update("delete from sys_role where role_code like ?", prefix + "%");
    jdbc.update("delete dd from doctor_department dd join doctor d on d.id=dd.doctor_id where d.doctor_no like ?", prefix + "%");
    jdbc.update("delete from doctor where doctor_no like ?", prefix + "%");
    jdbc.update("delete from outpatient_department where outpatient_code like ?", prefix + "%");
    jdbc.update("delete from department where department_code like ?", prefix + "%");
    jdbc.update("delete from hospital_campus where campus_code like ?", prefix + "%");
    jdbc.update("delete from hospital where hospital_code like ?", prefix + "%");
    jdbc.update("delete from sys_department where dept_code like ?", prefix + "%");
    jdbc.update("delete from sys_organization where org_code like ?", prefix + "%");
  }
}
