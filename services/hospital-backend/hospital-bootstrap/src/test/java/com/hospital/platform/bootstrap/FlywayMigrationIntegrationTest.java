package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {
  @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @Test void appliesFoundationMigrationsToRealMysql() throws Exception {
    var result = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").load().migrate();
    assertThat(result.migrationsExecuted).isEqualTo(34);
    try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
         var statement = connection.createStatement();
         var rows = statement.executeQuery("select count(*) from information_schema.tables where table_schema=database() and table_name in ('sys_user','sys_role','sys_permission','sys_audit_log','sys_file','sys_role_data_scope','hospital','hospital_campus','hospital_building','hospital_floor','department','doctor','doctor_title','medical_specialty','doctor_department','doctor_specialty','clinic_type','schedule_session_definition','schedule_template','schedule_template_rule','doctor_schedule','schedule_slot','schedule_change_log','patient','patient_member','patient_refresh_token','patient_hospital_card','external_patient_reference','appointment','appointment_status_history','clinic_room','visit_encounter','visit_encounter_status_history','queue_ticket','queue_sequence_counter')")) {
      rows.next();
      assertThat(rows.getInt(1)).isEqualTo(35);
      try(var paymentTables=connection.createStatement();var paymentRows=paymentTables.executeQuery("select count(*) from information_schema.tables where table_schema=database() and table_name in ('registration_fee_rule','registration_order','payment_order','payment_transaction','payment_callback_event','payment_audit_event','test_payment_provider_order','refund_order','refund_callback_event','payment_reconciliation_record','test_refund_provider_order')")) {
        paymentRows.next();assertThat(paymentRows.getInt(1)).isEqualTo(11);
      }
      try(var medicationTables=connection.createStatement();var medicationRows=medicationTables.executeQuery("select count(*) from information_schema.tables where table_schema=database() and table_name in ('drug_catalog','hospital_drug_formulary','pharmacy_location','pharmacy_user_scope','pharmacy_inventory','pharmacy_inventory_lot','outpatient_prescription','prescription_item','prescription_safety_alert','pharmacy_review','pharmacy_stock_reservation','dispensing_record','dispensing_item','medication_return_order','medication_return_item','medication_audit','pharmacy_inventory_adjustment')")) {
        medicationRows.next();assertThat(medicationRows.getInt(1)).isEqualTo(17);
      }
      try(var diagnosticTables=connection.createStatement();var diagnosticRows=diagnosticTables.executeQuery("select count(*) from information_schema.tables where table_schema=database() and table_name in ('clinical_service_catalog','clinical_order','clinical_order_item','lab_specimen','lab_result','lab_result_item','diagnostic_report','diagnostic_report_revision','imaging_study','critical_result_alert','clinical_result_review','clinical_integration_anomaly','clinical_diagnostic_audit','clinical_execution_user_scope')")) {
        diagnosticRows.next();assertThat(diagnosticRows.getInt(1)).isEqualTo(14);
      }
      statement.executeUpdate("insert into hospital(hospital_code,name,introduction,published) values('zero-h','Zero Hospital','smoke',1)");
      statement.executeUpdate("insert into hospital_campus(hospital_id,campus_code,name,published) values(1,'zero-c','Zero Campus',1)");
      statement.executeUpdate("insert into sys_organization(org_code,org_name,org_type) values('zero-o','Zero Org','TEST')");
      statement.executeUpdate("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(1,'zero-a','Zero Admin','TEST')");
      statement.executeUpdate("insert into department(organization_id,administrative_department_id,hospital_id,campus_id,department_code,department_name,published) values(1,1,1,1,'zero-d','Zero Department',1)");
      statement.executeUpdate("insert into clinic_type(code,name,published) values('zero-ct','Zero Clinic',1)");
      statement.executeUpdate("insert into outpatient_department(department_id,clinic_type_id,outpatient_code,name,published) values(1,1,'zero-od','Zero Outpatient',1)");
      statement.executeUpdate("insert into doctor(doctor_no,name_ciphertext,name,published) values('zero-doc','Zero Doctor','Zero Doctor',1)");
      statement.executeUpdate("insert into doctor_department(doctor_id,department_id,is_primary) values(1,1,1)");
      statement.executeUpdate("insert into schedule_session_definition(hospital_id,code,name,start_time,end_time) values(1,'zero-s','Zero Session','08:00:00','09:00:00')");
      statement.executeUpdate("insert into schedule_template(template_code,name,doctor_id,campus_id,outpatient_department_id,clinic_type_id,valid_from,valid_to,quota_per_session,published) values('zero-t','Zero Template',1,1,1,1,current_date(),date_add(current_date(),interval 7 day),30,1)");
      statement.executeUpdate("insert into schedule_template_rule(template_id,day_of_week,session_definition_id) values(1,dayofweek(current_date())+6,1)");
      statement.executeUpdate("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,clinic_type_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,template_id,total_quota) values('zero-sch',1,1,1,1,current_date(),1,'08:00:00','09:00:00','PUBLISHED','TEMPLATE',1,30)");
      var smoke = statement.executeQuery("select count(*) from doctor_schedule s join schedule_template t on t.id=s.template_id join schedule_template_rule r on r.template_id=t.id where s.schedule_status='PUBLISHED' and s.total_quota=30");
      smoke.next();
      assertThat(smoke.getInt(1)).isEqualTo(1);
    }
  }
}
