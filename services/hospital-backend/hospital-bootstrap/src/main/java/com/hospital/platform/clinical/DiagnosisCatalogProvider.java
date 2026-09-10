package com.hospital.platform.clinical;

import java.util.List;

public interface DiagnosisCatalogProvider {
  List<ClinicalService.DiagnosisCatalogItem> search(String keyword);
  ClinicalService.DiagnosisCatalogItem require(String codeSystem, String code);
}
