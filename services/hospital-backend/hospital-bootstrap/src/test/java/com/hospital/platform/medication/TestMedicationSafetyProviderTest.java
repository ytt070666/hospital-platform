package com.hospital.platform.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies TEST-only safety behavior without relying on any development patient data. */
class TestMedicationSafetyProviderTest {
  @Test void activeAllergyBlocksAndRemovingItReenablesOnlyTheBlockedDrug() {
    var activeAllergy = new AtomicBoolean(false);
    JdbcTemplate jdbc = new JdbcTemplate() {
      @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return type.cast(activeAllergy.get() ? Long.valueOf(1) : Long.valueOf(0));
      }
    };
    var provider = new TestMedicationSafetyProvider(jdbc);

    // NO_KNOWN_ALLERGIES means there is no ACTIVE allergy fact, therefore no allergy BLOCK.
    assertThat(provider.check(11, 22, List.of("TEST_DRUG_002"))).isEmpty();

    // A later clinician-recorded ACTIVE allergy is the safety fact that must be rechecked.
    activeAllergy.set(true);
    assertThat(provider.check(11, 22, List.of("TEST_DRUG_002")))
      .extracting(MedicationSafetyProvider.Alert::severity, MedicationSafetyProvider.Alert::ruleCode)
      .containsExactly(tuple("BLOCK", "TEST_ALLERGY_DRUG_002"));
    assertThat(provider.check(11, 22, List.of("TEST_DRUG_003")))
      .extracting(MedicationSafetyProvider.Alert::severity, MedicationSafetyProvider.Alert::ruleCode)
      .containsExactly(tuple("WARNING", "TEST_WARNING_DRUG_003"));

    activeAllergy.set(false);
    assertThat(provider.check(11, 22, List.of("TEST_DRUG_002"))).isEmpty();
  }
}
