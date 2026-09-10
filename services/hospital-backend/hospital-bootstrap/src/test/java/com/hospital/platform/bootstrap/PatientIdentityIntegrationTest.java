package com.hospital.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.iam.application.PatientTokenService;
import com.hospital.platform.patient.PatientIdentityService;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "hospital.patient.sms.test-enabled=true")
@AutoConfigureMockMvc
class PatientIdentityIntegrationTest {
  @Autowired PatientIdentityService patients;
  @Autowired PatientTokenService tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  private final java.util.List<Long> patientIds = new java.util.ArrayList<>();

  private PatientTokenService.TokenPair login() {
    String mobile = "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    var sent = patients.sendCode(mobile, "127.0.0.1");
    var pair = patients.login(mobile, sent.testCode(), "WEB_PATIENT", "127.0.0.1", "test");
    patientIds.add(tokens.authenticate(pair.accessToken()).patientId());
    return pair;
  }

  private PatientIdentityService.MemberRequest member(String relation, String name, String id) {
    return new PatientIdentityService.MemberRequest(relation, name, "FEMALE", LocalDate.of(1990, 1, 1), id == null ? null : "PASSPORT", id, null, null);
  }

  private String passport() { return "P" + UUID.randomUUID().toString().replace("-", "").toUpperCase(); }

  @Test void twoPatientsOwnIndependentSelfMembersAndIdorIsRejected() throws Exception {
    var a = login(); var b = login();
    long aId = tokens.authenticate(a.accessToken()).patientId();
    long bId = tokens.authenticate(b.accessToken()).patientId();
    assertThat(aId).isNotEqualTo(bId);
    long selfA = patients.createMember(aId, member("SELF", "患者A本人", passport()));
    long selfB = patients.createMember(bId, member("SELF", "患者B本人", passport()));
    assertThat(jdbc.queryForObject("select patient_id from patient_member where id=?", Long.class, selfA)).isEqualTo(aId);
    assertThat(jdbc.queryForObject("select patient_id from patient_member where id=?", Long.class, selfB)).isEqualTo(bId);
    mvc.perform(get("/api/v1/patient/members/{id}", selfB).header("Authorization", "Bearer " + a.accessToken())).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/patient/members/{id}/disable", selfB).header("Authorization", "Bearer " + a.accessToken())).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/patient/members/{id}/default", selfB).header("Authorization", "Bearer " + a.accessToken())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/patient/members/{id}", selfA).header("Authorization", "Bearer " + b.accessToken())).andExpect(status().isForbidden());
  }

  @Test void duplicateSelfAndIdentityAreSeparatedAndNonSelfMembersAreUnlimited() {
    var a = login(); long aId = tokens.authenticate(a.accessToken()).patientId(); String identity = passport();
    patients.createMember(aId, member("SELF", "本人", identity));
    assertThatThrownBy(() -> patients.createMember(aId, member("SELF", "第二本人", passport()))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.MEMBER_SELF_DUPLICATED);
    for (String relation : java.util.List.of("CHILD", "CHILD", "PARENT", "PARENT")) patients.createMember(aId, member(relation, "成员" + UUID.randomUUID(), null));
    assertThat(jdbc.queryForObject("select count(*) from patient_member where patient_id=? and is_self=0 and deleted=0", Long.class, aId)).isEqualTo(4);
    var b = login(); long bId = tokens.authenticate(b.accessToken()).patientId();
    assertThatThrownBy(() -> patients.createMember(bId, member("SELF", "重复证件", identity))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.IDENTITY_DUPLICATED);
  }

  @Test void logoutInvalidatesAlreadyIssuedPatientAccessToken() {
    var pair = login();
    patients.logout(pair.refreshToken());
    assertThatThrownBy(() -> tokens.authenticate(pair.accessToken()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).errorCode())
        .isEqualTo(ErrorCode.AUTH_003);
  }

  @AfterEach void cleanup() {
    if (patientIds.isEmpty()) return;
    String marks = String.join(",", java.util.Collections.nCopies(patientIds.size(), "?"));
    jdbc.update("delete from patient_refresh_token where patient_id in (" + marks + ")", patientIds.toArray());
    jdbc.update("delete from patient_member where patient_id in (" + marks + ")", patientIds.toArray());
    jdbc.update("delete from patient where id in (" + marks + ")", patientIds.toArray());
  }
}
