package com.hospital.platform.medication;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit TEST-only fixtures; this is not a real interaction or contraindication knowledge base. */
@Component
public class TestMedicationSafetyProvider implements MedicationSafetyProvider {
  private final JdbcTemplate jdbc;
  public TestMedicationSafetyProvider(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @Override public List<Alert> check(long patientId,long memberId,List<String> codes) {
    List<Alert> result=new ArrayList<>();
    Long allergy=jdbc.queryForObject("select count(*) from patient_allergy where patient_id=? and member_id=? and clinical_status='ACTIVE' and deleted=0 and allergen_code='TEST_ALLERGY_DRUG_002'",Long.class,patientId,memberId);
    if(codes.contains("TEST_DRUG_002")&&allergy!=null&&allergy>0) result.add(new Alert("BLOCK","TEST_ALLERGY_DRUG_002","测试规则：活动测试过敏与 TEST_DRUG_002 冲突"));
    if(codes.contains("TEST_DRUG_003")) result.add(new Alert("WARNING","TEST_WARNING_DRUG_003","测试规则：TEST_DRUG_003 需要医生确认"));
    return result;
  }
}
