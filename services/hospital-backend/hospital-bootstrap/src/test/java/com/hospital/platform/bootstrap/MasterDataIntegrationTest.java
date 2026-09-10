package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.masterdata.MasterDataService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MasterDataIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired MasterDataService master;
  private final String prefix = "md_" + UUID.randomUUID().toString().substring(0, 8);

  @Test void createsPublishedMasterDataAndKeepsPublicDoctorDtoSafe() {
    long hospital = master.createHospital(new MasterDataService.HospitalRequest(prefix + "h", "测试医院", null, null, "UNKNOWN", null, "简介", null, null, null, null, null, null, null, null, true, 0, true, 0), 1);
    long campus = master.createCampus(new MasterDataService.CampusRequest(hospital, prefix + "c", "测试院区", null, null, null, null, null, null, null, null, true, 0, true, 0));
    long building = master.createBuilding(new MasterDataService.BuildingRequest(campus, prefix + "b", "门诊楼", null, null, true, 0, true, 0));
    master.createFloor(new MasterDataService.FloorRequest(building, "F1", "一层", 1, null, null, true, 0, true, 0));
    long organization = insertOrganization();
    long administrativeDepartment = insertAdministrativeDepartment(organization);
    long department = master.createDepartment(new MasterDataService.DepartmentRequest(organization, administrativeDepartment, hospital, campus, null, prefix + "d", "内科", null, "CLINICAL", "公开科室", null, null, null, building, null, null, true, 0, true, 0));
    long title = master.createCatalog("titles", new MasterDataService.CatalogRequest(prefix + "t", "主任医师", null, true, 0, true, 0));
    long specialty = master.createCatalog("specialties", new MasterDataService.CatalogRequest(prefix + "s", "心血管", null, true, 0, true, 0));
    long doctor = master.createDoctor(new MasterDataService.DoctorRequest(prefix + "doc", null, "张医生", null, null, title, "公开简介", null, null, "心血管疾病", null, true, 0, true, List.of(department), department, List.of(specialty), 0));

    var publicDoctor = master.publicDoctor(doctor);
    assertThat(publicDoctor).containsEntry("name", "张医生").doesNotContainKeys("userId", "doctorNo", "status", "published", "version");
    assertThat(publicDoctor.get("departments")).asList().hasSize(1);

    var selfParent = new MasterDataService.DepartmentRequest(organization, administrativeDepartment, hospital, campus, department, prefix + "d", "内科", null, "CLINICAL", "公开科室", null, null, null, building, null, null, true, 0, true, 0);
    assertThatThrownBy(() -> master.updateDepartment(department, selfParent)).isInstanceOf(BusinessException.class);
  }

  private long insertOrganization() { jdbc.update("insert into sys_organization(org_code,org_name,org_type) values(?,?,?)", prefix + "org", "主数据测试组织", "TEST"); return id("select id from sys_organization where org_code=?", prefix + "org"); }
  private long insertAdministrativeDepartment(long organization) { jdbc.update("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,?)", organization, prefix + "admin", "主数据测试行政科室", "TEST"); return id("select id from sys_department where organization_id=? and dept_code=?", organization, prefix + "admin"); }
  private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

  @AfterEach void clean() {
    jdbc.update("delete ds from doctor_specialty ds join doctor d on d.id=ds.doctor_id where d.doctor_no like ?", prefix + "%");
    jdbc.update("delete dd from doctor_department dd join doctor d on d.id=dd.doctor_id where d.doctor_no like ?", prefix + "%");
    jdbc.update("delete from doctor where doctor_no like ?", prefix + "%");
    jdbc.update("delete from department where department_code like ?", prefix + "%");
    jdbc.update("delete from doctor_title where code like ?", prefix + "%");
    jdbc.update("delete from medical_specialty where code like ?", prefix + "%");
    jdbc.update("delete from hospital_floor where floor_code='F1' and building_id in (select id from hospital_building where building_code=?)", prefix + "b");
    jdbc.update("delete from hospital_building where building_code=?", prefix + "b");
    jdbc.update("delete from hospital_campus where campus_code=?", prefix + "c");
    jdbc.update("delete from hospital where hospital_code=?", prefix + "h");
    jdbc.update("delete from sys_department where dept_code=?", prefix + "admin");
    jdbc.update("delete from sys_organization where org_code=?", prefix + "org");
  }
}
