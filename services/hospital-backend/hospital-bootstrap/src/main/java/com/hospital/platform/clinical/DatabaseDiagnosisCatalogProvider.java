package com.hospital.platform.clinical;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads the hospital-imported catalogue in production and the deliberately small TEST catalogue only in non-production. */
@Component public class DatabaseDiagnosisCatalogProvider implements DiagnosisCatalogProvider {
  private final JdbcTemplate jdbc; private final String mode;
  public DatabaseDiagnosisCatalogProvider(JdbcTemplate jdbc, @Value("${hospital.clinical.diagnosis-catalog-mode:HOSPITAL}") String mode) { this.jdbc=jdbc; this.mode=mode==null?"HOSPITAL":mode; }
  @Override public List<ClinicalService.DiagnosisCatalogItem> search(String keyword) {
    String q=keyword==null?"":keyword.trim(); if(q.length()>64) throw new BusinessException(ErrorCode.VALIDATION);
    String source="TEST".equalsIgnoreCase(mode)?"TEST":"HOSPITAL";
    return jdbc.queryForList("select code_system,diagnosis_code,diagnosis_name from diagnosis_catalog where source=? and status=1 and (diagnosis_code like ? or diagnosis_name like ?) order by diagnosis_name limit 50",source,"%"+q+"%","%"+q+"%").stream().map(this::item).toList();
  }
  @Override public ClinicalService.DiagnosisCatalogItem require(String system,String code) {
    String source="TEST".equalsIgnoreCase(mode)?"TEST":"HOSPITAL";
    return jdbc.queryForList("select code_system,diagnosis_code,diagnosis_name from diagnosis_catalog where source=? and status=1 and code_system=? and diagnosis_code=?",source,system,code).stream().findFirst().map(this::item).orElseThrow(()->new BusinessException(ErrorCode.VALIDATION));
  }
  private ClinicalService.DiagnosisCatalogItem item(Map<String,Object> row) { return new ClinicalService.DiagnosisCatalogItem(text(row,"code_system"),text(row,"diagnosis_code"),text(row,"diagnosis_name")); }
  private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:String.valueOf(value);}
}
