package com.hospital.platform.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.bootstrap.HospitalApplication;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.patient.PatientCrypto;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Statement;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Medication workflow tests use disposable MySQL/Redis containers only. */
@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(classes=HospitalApplication.class, properties={"hospital.payment.worker-enabled=false","hospital.bootstrap.admin-username=","hospital.minio.endpoint=http://localhost:9000","hospital.minio.access-key=test-access","hospital.minio.secret-key=test-secret","spring.rabbitmq.listener.simple.auto-startup=false"})
class MedicationCoreIntegrationTest {
  @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4");
  @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
  static final String key=key();
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");r.add("hospital.security.jwt-secret",()->key);r.add("hospital.patient.encryption-key",()->key);r.add("hospital.patient.hash-key",()->key);r.add("hospital.payment.test-signing-key",()->key);r.add("hospital.minio.access-key",()->"test-access");r.add("hospital.minio.secret-key",()->"test-secret");}
  @Autowired JdbcTemplate jdbc; @Autowired MedicationService medication; @Autowired PatientCrypto crypto;
  long doctorUser,pharmacist,patient,member,encounter,pharmacy,drug1,drug2,lot1;

  @BeforeEach void fixture(){
    String p="rx_"+UUID.randomUUID().toString().replace("-","").substring(0,12); long hospital=insert("insert into hospital(hospital_code,name,published) values(?,?,1)",p,"处方测试医院"); long campus=insert("insert into hospital_campus(hospital_id,campus_code,name,published) values(?,?,?,1)",hospital,p,"测试院区"); long org=insert("insert into sys_organization(org_code,org_name,org_type) values(?,?, 'TEST')",p,"测试组织"); long admin=insert("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,'TEST')",org,p,"测试行政科室"); long department=insert("insert into department(organization_id,administrative_department_id,hospital_id,campus_id,department_code,department_name,published) values(?,?,?,?,?,?,1)",org,admin,hospital,campus,p,"测试门诊科"); long clinic=insert("insert into clinic_type(code,name,published) values(?,?,1)",p,"测试门诊类型"); long outpatient=insert("insert into outpatient_department(department_id,clinic_type_id,outpatient_code,name,published) values(?,?,?,?,1)",department,clinic,p,"测试门诊部"); long session=insert("insert into schedule_session_definition(hospital_id,code,name,start_time,end_time) values(?,?,?,'08:00:00','09:00:00')",hospital,p,"上午");
    doctorUser=insert("insert into sys_user(username,password_hash,display_name) values(?,?,?)",p+"_doctor","x","测试医生"); pharmacist=insert("insert into sys_user(username,password_hash,display_name) values(?,?,?)",p+"_pharmacy","x","测试药师"); long doctor=insert("insert into doctor(doctor_no,user_id,name_ciphertext,name,published) values(?,?,?,?,1)",p,doctorUser,crypto.encrypt("测试医生"),"测试医生"); patient=insert("insert into patient(patient_no,name_ciphertext) values(?,?)",p,crypto.encrypt("测试患者")); member=insert("insert into patient_member(patient_id,member_no,relationship_code,name_encrypted) values(?,?,'SELF',?)",patient,p,crypto.encrypt("测试患者")); long schedule=insert("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,clinic_type_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,date_add(current_date(),interval 2 day),?,'08:00:00','09:00:00','PUBLISHED','MANUAL',100)",p,doctor,campus,outpatient,clinic,session); long appointment=insert("insert into appointment(appointment_no,patient_id,member_id,schedule_id,status,source_client,idempotency_key,request_fingerprint,booked_at) values(?,?,?,?,'BOOKED','WEB_PATIENT',?,?,current_timestamp(3))",p,patient,member,schedule,p+"_key",p+"_hash");
    encounter=insert("insert into visit_encounter(encounter_no,appointment_id,patient_id,member_id,doctor_id,department_id,outpatient_department_id,campus_id,schedule_id,encounter_date,status) values(?,?,?,?,?,?,?,?,?,current_date(),'IN_CONSULTATION')",p,appointment,patient,member,doctor,department,outpatient,campus,schedule);
    long record=insert("insert into outpatient_medical_record(record_no,encounter_id,patient_id,member_id,doctor_id,department_id,status,created_by,updated_by) values(?,?,?,?,?,?, 'SIGNED',?,?)",p,encounter,patient,member,doctor,department,doctorUser,doctorUser); long revision=insert("insert into outpatient_medical_record_revision(medical_record_id,revision_no,revision_type,status,created_by,signed_by,signed_at,content_hash) values(?,1,'INITIAL','SIGNED',?,?,current_timestamp(3),'test')",record,doctorUser,doctorUser); jdbc.update("update outpatient_medical_record set current_revision_id=? where id=?",revision,record); jdbc.update("insert into encounter_diagnosis(encounter_id,medical_record_revision_id,diagnosis_type,code_system,diagnosis_code,diagnosis_name_snapshot,status,sort_order,entered_by) values(?,?, 'PRIMARY','TEST','T001','测试性上呼吸道感染','PROVISIONAL',0,?)",encounter,revision,doctorUser);
    drug1=id("select id from drug_catalog where drug_code='TEST_DRUG_001'"); drug2=id("select id from drug_catalog where drug_code='TEST_DRUG_002'"); jdbc.update("insert into hospital_drug_formulary(hospital_id,drug_id,hospital_drug_code,effective_from) values(?,?,?,current_date())",hospital,drug1,p+"_1"); jdbc.update("insert into hospital_drug_formulary(hospital_id,drug_id,hospital_drug_code,effective_from) values(?,?,?,current_date())",hospital,drug2,p+"_2"); pharmacy=insert("insert into pharmacy_location(hospital_id,campus_id,pharmacy_code,pharmacy_name) values(?,?,?,?)",hospital,campus,p,"测试药房"); jdbc.update("insert into pharmacy_user_scope(user_id,pharmacy_id) values(?,?)",pharmacist,pharmacy); long inventory=insert("insert into pharmacy_inventory(pharmacy_id,drug_id) values(?,?)",pharmacy,drug1); lot1=insert("insert into pharmacy_inventory_lot(inventory_id,pharmacy_id,drug_id,batch_no,expiry_date,on_hand_quantity,available_quantity) values(?,?,?,?,date_add(current_date(),interval 90 day),10,10)",inventory,pharmacy,drug1,p);
  }
  @Test void activeAllergyBlocksSubmitAndSignedPrescriptionIsImmutableAndTamperDetectable(){
    jdbc.update("insert into patient_allergy(patient_id,member_id,allergen_type,allergen_code,allergen_name,clinical_status,verification_status,recorded_by) values(?,?, 'DRUG','TEST_ALLERGY_DRUG_002','测试过敏','ACTIVE','CLINICIAN_RECORDED',?)",patient,member,doctorUser);
    var blocked=draft(drug2,BigDecimal.ONE); assertThatThrownBy(()->medication.submit(doctorUser,encounter,blocked.id(),new MedicationService.SubmitInput((int)blocked.version(),""),false)).isInstanceOf(BusinessException.class).hasMessage("用药安全规则阻断处方提交");
    var signed=draft(drug1,new BigDecimal("2")); assertThatThrownBy(()->medication.addItem(doctorUser,encounter,signed.id(),item(drug1,BigDecimal.ONE))).isInstanceOf(BusinessException.class).hasMessage("已签署处方不可直接修改"); assertThat(medication.verifyIntegrity(doctorUser,encounter,signed.id())).isTrue(); jdbc.update("update prescription_item set quantity=3 where prescription_id=?",signed.id()); assertThat(medication.verifyIntegrity(doctorUser,encounter,signed.id())).isFalse();
  }
  @Test void approvalReservesAndDispenseDeductsOnlyOnce(){
    var signed=draft(drug1,new BigDecimal("2")); var submitted=medication.submit(doctorUser,encounter,signed.id(),new MedicationService.SubmitInput((int)signed.version(),""),false); var approved=medication.review(pharmacist,submitted.id(),new MedicationService.ReviewInput(pharmacy,"APPROVED",null,null)); assertThat(approved.status()).isEqualTo("PHARMACY_APPROVED"); assertThat(decimal("select available_quantity from pharmacy_inventory_lot where id=?",lot1)).isEqualByComparingTo("8"); var dispensed=medication.dispense(pharmacist,approved.id(),pharmacy); assertThat(dispensed.status()).isEqualTo("DISPENSED"); assertThat(decimal("select on_hand_quantity from pharmacy_inventory_lot where id=?",lot1)).isEqualByComparingTo("8"); assertThat(decimal("select reserved_quantity from pharmacy_inventory_lot where id=?",lot1)).isZero(); assertThat(jdbc.queryForObject("select count(*) from dispensing_record where prescription_id=?",Long.class,approved.id())).isEqualTo(1L);
  }
  MedicationService.PrescriptionView draft(long drug,BigDecimal quantity){var p=medication.createDraft(doctorUser,encounter,null);p=medication.addItem(doctorUser,encounter,p.id(),item(drug,quantity));return medication.sign(doctorUser,encounter,p.id(),new MedicationService.VersionInput((int)p.version()));}
  MedicationService.ItemInput item(long drug,BigDecimal quantity){return new MedicationService.ItemInput(drug,BigDecimal.ONE,"tablet","PO","QD",2,"DAY",quantity,"tablet",null);}
  long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
  long id(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));} BigDecimal decimal(String sql,Object...args){return jdbc.queryForObject(sql,BigDecimal.class,args);} static String key(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
}
