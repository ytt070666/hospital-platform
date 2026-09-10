package com.hospital.platform.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.bootstrap.HospitalApplication;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.iam.application.AuthService;
import com.hospital.platform.patient.PatientCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Statement;
import java.util.Base64;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the real service boundary: catalog, execution data scope and manual patient release. */
@Testcontainers
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@SpringBootTest(classes=HospitalApplication.class, properties={"hospital.payment.worker-enabled=false","hospital.bootstrap.admin-username=","hospital.minio.endpoint=http://localhost:9000","hospital.minio.access-key=test-access","hospital.minio.secret-key=test-secret","spring.rabbitmq.listener.simple.auto-startup=false","hospital.clinical.diagnostic.patient-release-policy=MANUAL_RELEASE"})
class DiagnosticScopeAndReleaseIntegrationTest {
  @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4");
  @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
  static final String key=key();
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");r.add("hospital.security.jwt-secret",()->key);r.add("hospital.patient.encryption-key",()->key);r.add("hospital.patient.hash-key",()->key);r.add("hospital.payment.test-signing-key",()->key);}
  @Autowired JdbcTemplate jdbc; @Autowired DiagnosticService diagnostics; @Autowired PatientCrypto crypto; @Autowired PasswordEncoder passwords; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
  long doctorUser,otherDoctorUser,collectorUser,technicianUser,reviewerUser,radiologyUser,radiologyTechnicianUser,auditorUser,superAdminUser,doctor,patient,member,encounter,department;
  String loginPassword;

  @BeforeEach void fixture(){
    String p="dx_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    long hospital=insert("insert into hospital(hospital_code,name,published) values(?,?,1)",p,"诊断测试医院"); long campus=insert("insert into hospital_campus(hospital_id,campus_code,name,published) values(?,?,?,1)",hospital,p,"测试院区"); long org=insert("insert into sys_organization(org_code,org_name,org_type) values(?,?, 'TEST')",p,"测试组织"); long admin=insert("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,'TEST')",org,p,"测试行政科室"); department=insert("insert into department(organization_id,administrative_department_id,hospital_id,campus_id,department_code,department_name,published) values(?,?,?,?,?,?,1)",org,admin,hospital,campus,p,"测试门诊科"); long clinic=insert("insert into clinic_type(code,name,published) values(?,?,1)",p,"测试门诊类型"); long outpatient=insert("insert into outpatient_department(department_id,clinic_type_id,outpatient_code,name,published) values(?,?,?,?,1)",department,clinic,p,"测试门诊部"); long session=insert("insert into schedule_session_definition(hospital_id,code,name,start_time,end_time) values(?,?,?,'08:00:00','09:00:00')",hospital,p,"上午");
    loginPassword="E2E74D-"+UUID.randomUUID()+"-Pass"; doctorUser=user(p+"_doctor","测试医生"); otherDoctorUser=user(p+"_doctor_b","测试医生B"); collectorUser=user(p+"_collector","测试采集员"); technicianUser=user(p+"_technician","测试技术员"); reviewerUser=user(p+"_reviewer","测试审核员"); radiologyUser=user(p+"_radiology","测试影像医生"); radiologyTechnicianUser=user(p+"_radiology_tech","测试影像技师"); auditorUser=user(p+"_auditor","测试审计员"); superAdminUser=user(p+"_superadmin","测试超管"); grant(doctorUser,"CLINICAL_DOCTOR");grant(otherDoctorUser,"CLINICAL_DOCTOR");grant(collectorUser,"LAB_COLLECTOR");grant(technicianUser,"LAB_TECHNICIAN");grant(reviewerUser,"LAB_REVIEWER");grant(radiologyUser,"RADIOLOGY_REPORTING_DOCTOR");grant(radiologyTechnicianUser,"RADIOLOGY_TECHNICIAN");grant(auditorUser,"CLINICAL_RESULT_AUDITOR");grant(superAdminUser,"SUPER_ADMIN"); doctor=insert("insert into doctor(doctor_no,user_id,name_ciphertext,name,published) values(?,?,?,?,1)",p,doctorUser,crypto.encrypt("测试医生"),"测试医生"); patient=insert("insert into patient(patient_no,name_ciphertext) values(?,?)",p,crypto.encrypt("测试患者")); member=insert("insert into patient_member(patient_id,member_no,relationship_code,name_encrypted) values(?,?,'SELF',?)",patient,p,crypto.encrypt("测试患者")); long schedule=insert("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,clinic_type_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,date_add(current_date(),interval 2 day),?,'08:00:00','09:00:00','PUBLISHED','MANUAL',100)",p,doctor,campus,outpatient,clinic,session); long appointment=insert("insert into appointment(appointment_no,patient_id,member_id,schedule_id,status,source_client,idempotency_key,request_fingerprint,booked_at) values(?,?,?,?,'BOOKED','WEB_PATIENT',?,?,current_timestamp(3))",p,patient,member,schedule,p+"_key",p+"_hash");
    encounter=insert("insert into visit_encounter(encounter_no,appointment_id,patient_id,member_id,doctor_id,department_id,outpatient_department_id,campus_id,schedule_id,encounter_date,status) values(?,?,?,?,?,?,?,?,?,current_date(),'IN_CONSULTATION')",p,appointment,patient,member,doctor,department,outpatient,campus,schedule); long record=insert("insert into outpatient_medical_record(record_no,encounter_id,patient_id,member_id,doctor_id,department_id,status,created_by,updated_by) values(?,?,?,?,?,?, 'SIGNED',?,?)",p,encounter,patient,member,doctor,department,doctorUser,doctorUser); long revision=insert("insert into outpatient_medical_record_revision(medical_record_id,revision_no,revision_type,status,created_by,signed_by,signed_at,content_hash) values(?,1,'INITIAL','SIGNED',?,?,current_timestamp(3),'test')",record,doctorUser,doctorUser); jdbc.update("update outpatient_medical_record set current_revision_id=? where id=?",revision,record);
  }

  @Test void realLoginTokensEnforceClinicalRoleAndTreatmentBoundaries() throws Exception {
    String doctor=token(doctorUser),otherDoctor=token(otherDoctorUser),collector=token(collectorUser),technician=token(technicianUser),reviewer=token(reviewerUser),radiologist=token(radiologyUser),radiologyTech=token(radiologyTechnicianUser),auditor=token(auditorUser),superAdmin=token(superAdminUser);
    for(String token:java.util.List.of(doctor,otherDoctor,collector,technician,reviewer,radiologist,radiologyTech,auditor,superAdmin))mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    long service=id("select id from clinical_service_catalog where service_code='TEST_LAB_001'"); var order=diagnostics.create(doctorUser,encounter,"LAB",new DiagnosticService.OrderInput(service,"ROUTINE","真实令牌权限",null,null,null));
    mvc.perform(get("/api/v1/doctor/visits/{encounterId}/orders/{orderId}",encounter,order.id()).header("Authorization","Bearer "+doctor)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/doctor/visits/{encounterId}/orders/{orderId}",encounter,order.id()).header("Authorization","Bearer "+otherDoctor)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/lab/results/{resultId}/verify",999999L).header("Authorization","Bearer "+collector)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/lab/results/{resultId}/verify",999999L).header("Authorization","Bearer "+technician)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/diagnostic-reports/{reportId}/sign",999999L).contentType("application/json").content("{\"version\":0}").header("Authorization","Bearer "+radiologyTech)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/diagnostic-reports/{reportId}/sign",999999L).contentType("application/json").content("{\"version\":0}").header("Authorization","Bearer "+radiologist)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/doctor/visits/{encounterId}/orders/{orderId}",encounter,order.id()).header("Authorization","Bearer "+superAdmin)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/lab/results").header("Authorization","Bearer "+reviewer)).andExpect(status().isOk());
    long imagingService=id("select id from clinical_service_catalog where service_code='TEST_IMAGING_001'"); jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'IMAGING')",radiologyUser,department);
    var imagingDraft=diagnostics.create(doctorUser,encounter,"IMAGING",new DiagnosticService.OrderInput(imagingService,"ROUTINE","合法正文攻击", "CHEST",null,null)); var imagingOrder=diagnostics.place(doctorUser,encounter,imagingDraft.id(),new DiagnosticService.VersionInput(imagingDraft.version())); diagnostics.acquireStudy(radiologyUser,imagingOrder.id()); var report=diagnostics.createImagingReport(radiologyUser,imagingOrder.id(),new DiagnosticService.ReportInput("E2E74D FINAL ORIGINAL",null,"E2E74D FINAL ORIGINAL",null)); var finalReport=diagnostics.signReport(radiologyUser,report.id(),new DiagnosticService.VersionInput(report.version()));
    String originalFindings=jdbc.queryForObject("select findings from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id()); String originalHash=jdbc.queryForObject("select content_hash from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id()); long originalVersion=jdbc.queryForObject("select version from diagnostic_report where id=?",Long.class,report.id());
    mvc.perform(get("/api/v1/diagnostic-reports/{reportId}/audit",report.id()).header("Authorization","Bearer "+auditor)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/diagnostic-reports/{reportId}/sign",report.id()).contentType("application/json").content("{\"version\":0}").header("Authorization","Bearer "+auditor)).andExpect(status().isForbidden());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/diagnostic-reports/{reportId}/draft",report.id()).contentType("application/json").content(json.writeValueAsString(java.util.Map.ofEntries(java.util.Map.entry("version",finalReport.version()),java.util.Map.entry("findings","E2E74D ILLEGAL CHANGE"),java.util.Map.entry("impression","changed"),java.util.Map.entry("conclusion","changed"),java.util.Map.entry("status","DRAFT"),java.util.Map.entry("signedBy",otherDoctorUser),java.util.Map.entry("signedAt","2026-01-01T00:00:00Z"),java.util.Map.entry("contentHash","fake"),java.util.Map.entry("patientId",patient),java.util.Map.entry("encounterId",encounter),java.util.Map.entry("verified",true),java.util.Map.entry("critical",true)))).header("Authorization","Bearer "+radiologist)).andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("select findings from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id())).isEqualTo(originalFindings); assertThat(jdbc.queryForObject("select content_hash from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id())).isEqualTo(originalHash); assertThat(jdbc.queryForObject("select version from diagnostic_report where id=?",Long.class,report.id())).isEqualTo(originalVersion); assertThat(diagnostics.verifyReport(doctorUser,report.id())).isTrue();
  }

  @Test void scopeIsFailClosedAndManualReleaseProtectsPatientFeed(){
    assertThat(diagnostics.catalog("LAB").stream().map(DiagnosticService.ServiceItem::serviceCode)).contains("TEST_LAB_001","TEST_LAB_CRITICAL");
    long service=id("select id from clinical_service_catalog where service_code='TEST_LAB_CRITICAL'"); var draft=diagnostics.create(doctorUser,encounter,"LAB",new DiagnosticService.OrderInput(service,"STAT","测试临床指征",null,null,null)); var placed=diagnostics.place(doctorUser,encounter,draft.id(),new DiagnosticService.VersionInput(draft.version())); long specimen=id("select id from lab_specimen where clinical_order_id=?",placed.id());
    assertThatThrownBy(()->diagnostics.collect(collectorUser,specimen,new DiagnosticService.VersionInput(0))).isInstanceOf(BusinessException.class);
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",collectorUser,department); jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",technicianUser,department); jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",reviewerUser,department);
    var collected=diagnostics.collect(collectorUser,specimen,new DiagnosticService.VersionInput(0)); var received=diagnostics.receive(technicianUser,specimen,new DiagnosticService.VersionInput(collected.version())); var result=diagnostics.enterResult(technicianUser,new DiagnosticService.ResultInput(specimen,"LIS-"+UUID.randomUUID(),java.util.List.of(new DiagnosticService.ResultItemInput("TEST_K","钾","NUMERIC",new BigDecimal("9.1"),null,"mmol/L","mmol/L",new BigDecimal("3.5"),new BigDecimal("5.5"),null,"CRITICAL",true)))); assertThat(diagnostics.results(reviewerUser,"PRELIMINARY")).extracting(DiagnosticService.ResultView::id).contains(result.id()); var verified=diagnostics.verifyResult(reviewerUser,result.id());
    assertThat(diagnostics.patientReports(patient)).isEmpty(); long report=Objects.requireNonNull(verified.reportId()); jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'IMAGING')",radiologyUser,department); assertThatThrownBy(()->diagnostics.amendReport(radiologyUser,report,new DiagnosticService.ReportInput("越权修改",null,null,"不应允许"))).isInstanceOf(BusinessException.class); long alert=id("select id from critical_result_alert where clinical_order_id=?",placed.id()); assertThat(concurrentSuccesses(100,()->diagnostics.acknowledge(doctorUser,alert,new DiagnosticService.Reason("并发危急确认")))).as("only one critical-alert acknowledgement is accepted").isEqualTo(1); assertThat(jdbc.queryForObject("select count(*) from critical_result_alert where id=? and status='ACKNOWLEDGED' and acknowledged_by=?",Long.class,alert,doctorUser)).as("critical alert has one persisted acknowledgement").isEqualTo(1L); assertThat(concurrentSuccesses(100,()->diagnostics.review(doctorUser,report,new DiagnosticService.Reason("并发结果复核")))).as("only one clinical-result review is accepted").isEqualTo(1); assertThat(jdbc.queryForObject("select count(*) from clinical_result_review where report_id=? and doctor_id=?",Long.class,report,doctor)).as("one clinical-result review is persisted").isEqualTo(1L); diagnostics.releaseForPatient(doctorUser,report); assertThat(diagnostics.patientReports(patient)).hasSize(1); assertThat(jdbc.queryForObject("select count(*) from critical_result_alert where clinical_order_id=?",Long.class,placed.id())).as("critical alert is retained").isEqualTo(1L);
  }

  @Test void diagnosticTransitionsReplayAndSigningAreSingleWriterUnderOneHundredRequests(){
    long labService=id("select id from clinical_service_catalog where service_code='TEST_LAB_CRITICAL'");
    var labDraft=diagnostics.create(doctorUser,encounter,"LAB",new DiagnosticService.OrderInput(labService,"STAT","并发检验申请",null,null,null));
    assertThat(concurrentSuccesses(100,()->diagnostics.place(doctorUser,encounter,labDraft.id(),new DiagnosticService.VersionInput(0)))).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from clinical_order where id=? and status='PLACED'",Long.class,labDraft.id())).isEqualTo(1L);
    String providerKey="E2E74D-PROVIDER-"+UUID.randomUUID();
    assertThat(concurrentSuccesses(100,()->diagnostics.submitToProvider(doctorUser,encounter,labDraft.id(),new DiagnosticService.ProviderSubmissionInput(providerKey)))).isEqualTo(100);
    assertThat(jdbc.queryForObject("select count(*) from clinical_order where id=? and provider_submission_key=?",Long.class,labDraft.id(),providerKey)).isEqualTo(1L);
    long specimen=id("select id from lab_specimen where clinical_order_id=?",labDraft.id());
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",collectorUser,department);
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",technicianUser,department);
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",reviewerUser,department);
    assertThat(concurrentSuccesses(100,()->diagnostics.collect(collectorUser,specimen,new DiagnosticService.VersionInput(0)))).isEqualTo(1);
    assertThat(concurrentSuccesses(100,()->diagnostics.receive(technicianUser,specimen,new DiagnosticService.VersionInput(1)))).isEqualTo(1);
    String replayEvent="LIS-REPLAY-"+UUID.randomUUID();
    var replayInput=new DiagnosticService.ResultInput(specimen,replayEvent,java.util.List.of(new DiagnosticService.ResultItemInput("TEST_K","并发钾","NUMERIC",new BigDecimal("9.1"),null,"mmol/L","mmol/L",new BigDecimal("3.5"),new BigDecimal("5.5"),null,"CRITICAL",true)));
    assertThat(concurrentSuccesses(100,()->diagnostics.enterResult(technicianUser,replayInput))).isEqualTo(100);
    assertThat(jdbc.queryForObject("select count(*) from lab_result where provider_event_id=?",Long.class,replayEvent)).isEqualTo(1L);
    long result=id("select id from lab_result where provider_event_id=?",replayEvent);
    assertThat(concurrentSuccesses(100,()->diagnostics.verifyResult(reviewerUser,result))).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from critical_result_alert where clinical_order_id=?",Long.class,labDraft.id())).isEqualTo(1L);

    long imagingService=id("select id from clinical_service_catalog where service_code='TEST_IMAGING_001'");
    var imagingDraft=diagnostics.create(doctorUser,encounter,"IMAGING",new DiagnosticService.OrderInput(imagingService,"ROUTINE","并发影像申请","CHEST",null,null));
    var imagingOrder=diagnostics.place(doctorUser,encounter,imagingDraft.id(),new DiagnosticService.VersionInput(imagingDraft.version()));
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'IMAGING')",radiologyUser,department);
    diagnostics.acquireStudy(radiologyUser,imagingOrder.id());
    var report=diagnostics.createImagingReport(radiologyUser,imagingOrder.id(),new DiagnosticService.ReportInput("E2E74C 原始影像所见",null,"E2E74C 原始印象",null));
    var edited=diagnostics.updateDraftReport(radiologyUser,report.id(),new DiagnosticService.DraftReportInput(0,"E2E74D EDIT FIRST",null,"E2E74C 原始印象"));
    assertThatThrownBy(()->diagnostics.signReport(radiologyUser,report.id(),new DiagnosticService.VersionInput(0))).isInstanceOf(BusinessException.class);
    assertThat(concurrentSuccesses(100,()->diagnostics.signReport(radiologyUser,report.id(),new DiagnosticService.VersionInput(edited.version())))).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from diagnostic_report_revision where report_id=? and status='FINAL'",Long.class,report.id())).isEqualTo(1L);
    assertThat(diagnostics.verifyReport(doctorUser,report.id())).isTrue();
    assertThatThrownBy(()->diagnostics.updateDraftReport(radiologyUser,report.id(),new DiagnosticService.DraftReportInput(edited.version()+1,"E2E74D FINAL ATTACK",null,"E2E74C 原始印象"))).isInstanceOf(BusinessException.class);
    assertThat(jdbc.queryForObject("select findings from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id())).isEqualTo("E2E74D EDIT FIRST");

    var raceDraft=diagnostics.create(doctorUser,encounter,"IMAGING",new DiagnosticService.OrderInput(imagingService,"ROUTINE","签署编辑竞态","CHEST",null,null));
    var raceOrder=diagnostics.place(doctorUser,encounter,raceDraft.id(),new DiagnosticService.VersionInput(raceDraft.version()));
    diagnostics.acquireStudy(radiologyUser,raceOrder.id());
    var raceReport=diagnostics.createImagingReport(radiologyUser,raceOrder.id(),new DiagnosticService.ReportInput("E2E74D RACE ORIGINAL",null,"E2E74D RACE ORIGINAL",null));
    assertThat(raceSuccesses(()->diagnostics.signReport(radiologyUser,raceReport.id(),new DiagnosticService.VersionInput(0)),()->diagnostics.updateDraftReport(radiologyUser,raceReport.id(),new DiagnosticService.DraftReportInput(0,"E2E74D RACE EDIT",null,"E2E74D RACE EDIT")))).isEqualTo(1);
    long raceVersion=jdbc.queryForObject("select version from diagnostic_report where id=?",Long.class,raceReport.id());
    String raceStatus=jdbc.queryForObject("select status from diagnostic_report where id=?",String.class,raceReport.id());
    if("DRAFT".equals(raceStatus))diagnostics.signReport(radiologyUser,raceReport.id(),new DiagnosticService.VersionInput(raceVersion));
    assertThat(diagnostics.verifyReport(doctorUser,raceReport.id())).isTrue();
    var amendment=diagnostics.amendReport(radiologyUser,report.id(),new DiagnosticService.ReportInput("E2E74C 修订影像所见",null,"E2E74C 修订印象","E2E74C 必要修订原因"));
    assertThat(amendment.status()).isEqualTo("DRAFT"); assertThat(amendment.revisionNo()).isEqualTo(2L);
    var amendedFinal=diagnostics.signReport(radiologyUser,report.id(),new DiagnosticService.VersionInput(amendment.version()));
    assertThat(amendedFinal.status()).isEqualTo("FINAL");
    assertThat(diagnostics.reportHistory(radiologyUser,report.id()))
        .extracting(DiagnosticService.ReportRevisionView::revisionNo)
        .containsExactly(1L,2L);
    assertThatThrownBy(()->diagnostics.reportHistory(collectorUser,report.id()))
        .isInstanceOf(BusinessException.class);
    assertThat(jdbc.queryForObject("select findings from diagnostic_report_revision where report_id=? and revision_no=1",String.class,report.id())).isEqualTo("E2E74D EDIT FIRST");
    assertThat(jdbc.queryForObject("select findings from diagnostic_report_revision where report_id=? and revision_no=2",String.class,report.id())).isEqualTo("E2E74C 修订影像所见");
    assertThat(diagnostics.verifyReport(doctorUser,report.id())).isTrue();
    jdbc.update("update diagnostic_report_revision set findings='E2E74C 篡改内容' where report_id=? and revision_no=2",report.id());
    assertThat(diagnostics.verifyReport(doctorUser,report.id())).isFalse();
  }

  @Test void lateLabResultCanFinalizeAndBeReviewedWithoutReopeningCompletedEncounter() throws Exception {
    long service=id("select id from clinical_service_catalog where service_code='TEST_LAB_001'");
    var draft=diagnostics.create(doctorUser,encounter,"LAB",new DiagnosticService.OrderInput(service,"ROUTINE","迟到结果验收",null,null,null));
    var placed=diagnostics.place(doctorUser,encounter,draft.id(),new DiagnosticService.VersionInput(draft.version()));
    long specimen=id("select id from lab_specimen where clinical_order_id=?",placed.id());
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",collectorUser,department);
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",technicianUser,department);
    jdbc.update("insert into clinical_execution_user_scope(user_id,department_id,service_type) values(?,?, 'LAB')",reviewerUser,department);
    diagnostics.collect(collectorUser,specimen,new DiagnosticService.VersionInput(0));
    diagnostics.receive(technicianUser,specimen,new DiagnosticService.VersionInput(1));
    jdbc.update("update visit_encounter set status='COMPLETED',completed_at=current_timestamp(3) where id=?",encounter);
    String technician=token(technicianUser),reviewer=token(reviewerUser),doctor=token(doctorUser);
    String event="E2E74D-LATE-"+UUID.randomUUID();
    var entered=mvc.perform(post("/api/v1/lab/results").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("specimenId",specimen,"providerEventId",event,"items",java.util.List.of(java.util.Map.of("testCode","LATE_K","testName","迟到检验结果","valueType","NUMERIC","numericValue",new BigDecimal("4.2"),"unitCode","mmol/L","unitName","mmol/L","abnormalFlag","NORMAL","criticalFlag",false))))).header("Authorization","Bearer "+technician)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long resultId=json.readTree(entered).path("data").path("id").asLong();
    var finalized=mvc.perform(post("/api/v1/lab/results/{resultId}/verify",resultId).header("Authorization","Bearer "+reviewer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long reportId=json.readTree(finalized).path("data").path("reportId").asLong();
    mvc.perform(post("/api/v1/doctor/diagnostic-reports/{reportId}/review",reportId).contentType("application/json").content("{\"reason\":\"迟到结果复核\"}").header("Authorization","Bearer "+doctor)).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("select status from visit_encounter where id=?",String.class,encounter)).isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject("select status from diagnostic_report where id=?",String.class,reportId)).isEqualTo("FINAL");
    assertThat(jdbc.queryForObject("select count(*) from clinical_result_review where report_id=?",Long.class,reportId)).isEqualTo(1L);
  }

  int concurrentSuccesses(int attempts,ThrowingOperation operation){ExecutorService pool=Executors.newFixedThreadPool(16);CountDownLatch start=new CountDownLatch(1);var futures=new ArrayList<Future<Boolean>>();try{for(int i=0;i<attempts;i++)futures.add(pool.submit(()->{start.await();try{operation.run();return true;}catch(BusinessException expected){return false;}}));start.countDown();int successes=0;for(Future<Boolean> future:futures)if(future.get())successes++;return successes;}catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}}
  int raceSuccesses(ThrowingOperation first,ThrowingOperation second){ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);try{Future<Boolean>a=pool.submit(()->{start.await();try{first.run();return true;}catch(BusinessException expected){return false;}});Future<Boolean>b=pool.submit(()->{start.await();try{second.run();return true;}catch(BusinessException expected){return false;}});start.countDown();return (a.get()?1:0)+(b.get()?1:0);}catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}}
  @FunctionalInterface interface ThrowingOperation { void run() throws Exception; }

  String token(long userId)throws Exception{String username=jdbc.queryForObject("select username from sys_user where id=?",String.class,userId);var response=mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For","10.74."+(userId%250)+".1").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",username,"password",loginPassword,"clientType","E2E_TEST")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode tree=json.readTree(response);String token=tree.path("data").path("accessToken").asText();assertThat(token).isNotBlank();return token;}
  long user(String username,String displayName){return insert("insert into sys_user(username,password_hash,display_name) values(?,?,?)",username,passwords.encode(loginPassword),displayName);} void grant(long userId,String role){jdbc.update("insert into sys_user_role(user_id,role_id) select ?,id from sys_role where role_code=?",userId,role);} long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},keys);return Objects.requireNonNull(keys.getKey()).longValue();} long id(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));} static String key(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
}
