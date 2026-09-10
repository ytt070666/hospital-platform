package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.masterdata.MasterDataService;
import com.hospital.platform.scheduling.SchedulingService;
import com.hospital.platform.scheduling.SchedulingController;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchedulingPublicApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired MasterDataService master;
  @Autowired SchedulingService scheduling;
  @Autowired SchedulingController controller;
  private final String prefix = "e2e_schedule_" + UUID.randomUUID().toString().substring(0, 8);
  private long hospital, campus, organization, administrative, department, outpatient, doctor, session, schedule;

  @Test void publicQueryOnlyReturnsPublishedEnabledDataAndUsesSafeDtoShape() {
    createPublishedGraph();
    var result = scheduling.schedules(0, new SchedulingService.ScheduleFilter(null, null, null, null, LocalDate.now(), LocalDate.now().plusDays(7), 1, 20), true);
    assertThat(result.records()).extracting(row -> ((Number) row.get("id")).longValue()).contains(schedule);
    var row = result.records().stream().filter(value -> ((Number) value.get("id")).longValue() == schedule).findFirst().orElseThrow();
    assertThat(row).containsEntry("scheduleStatus", "PUBLISHED").containsEntry("doctorName", "公开排班医生");
    var publicRow = controller.publicSchedules(new SchedulingController.ScheduleQuery(null, null, null, null, LocalDate.now(), LocalDate.now().plusDays(7), 1, 20)).data().records().stream().filter(value -> ((Number) value.get("id")).longValue() == schedule).findFirst().orElseThrow();
    assertThat(publicRow).doesNotContainKeys("doctorId", "displayDoctorId", "originalDoctorId", "substituteDoctorId", "reservedQuota", "bookedQuota", "version", "source", "scheduleStatus");
    jdbc.update("update hospital_campus set published=0 where id=?", campus);
    assertThat(scheduling.schedules(0, new SchedulingService.ScheduleFilter(null, null, null, null, LocalDate.now(), LocalDate.now().plusDays(7), 1, 20), true).records()).noneMatch(value -> ((Number) value.get("id")).longValue() == schedule);
  }

  @Test void publicQueryRejectsHistoricalAndOverwideRanges() {
    assertThatThrownBy(() -> scheduling.schedules(0, new SchedulingService.ScheduleFilter(null, null, null, null, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1), 1, 20), true)).isInstanceOf(BusinessException.class);
  }

  private void createPublishedGraph() {
    hospital = master.createHospital(new MasterDataService.HospitalRequest(prefix + "h", "公开排班医院", null, null, "UNKNOWN", null, "公开排班测试医院", null, null, null, null, null, null, null, null, true, 0, true, 0), 1);
    campus = master.createCampus(new MasterDataService.CampusRequest(hospital, prefix + "c", "公开排班院区", null, null, null, null, null, null, null, null, true, 0, true, 0));
    jdbc.update("insert into sys_organization(org_code,org_name,org_type) values(?,?,?)", prefix + "o", "公开排班组织", "TEST");
    organization = id("select id from sys_organization where org_code=?", prefix + "o");
    jdbc.update("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,?)", organization, prefix + "a", "公开排班行政科", "TEST");
    administrative = id("select id from sys_department where organization_id=? and dept_code=?", organization, prefix + "a");
    department = master.createDepartment(new MasterDataService.DepartmentRequest(organization, administrative, hospital, campus, null, prefix + "d", "公开排班科室", null, "CLINICAL", "公开排班测试科室", null, null, null, null, null, null, true, 0, true, 0));
    outpatient = master.createOutpatientDepartment(new MasterDataService.OutpatientDepartmentRequest(department, null, prefix + "od", "公开门诊", null, true, 0, true, 0));
    doctor = master.createDoctor(new MasterDataService.DoctorRequest(prefix + "doc", null, "公开排班医生", null, null, null, "公开排班测试医生", null, null, null, null, true, 0, true, List.of(department), department, List.of(), 0));
    session = scheduling.createSession(new SchedulingService.SessionRequest(hospital, prefix + "s", "上午门诊", java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 0), 0, true, 0));
    jdbc.update("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,?,?,?,'PUBLISHED','MANUAL',?)", prefix + "no", doctor, campus, outpatient, LocalDate.now().plusDays(2), session, "08:00:00", "12:00:00", 12);
    schedule = id("select id from doctor_schedule where schedule_no=?", prefix + "no");
  }

  private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
  @AfterEach void clean() {
    jdbc.update("delete from schedule_slot where schedule_id in (select id from doctor_schedule where schedule_no like ?)", prefix + "%");
    jdbc.update("delete from schedule_change_log where schedule_id in (select id from doctor_schedule where schedule_no like ?)", prefix + "%");
    jdbc.update("delete from doctor_schedule where schedule_no like ?", prefix + "%");
    jdbc.update("delete from schedule_session_definition where code like ?", prefix + "%");
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
