package com.hospital.platform.iam.application;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues patient-only tokens; these never reference sys_user or sys_refresh_token. */
@Service
public class PatientTokenService {
  private static final String AUDIENCE = "hospital-patient-api";
  private final JdbcTemplate jdbc;
  private final SecretKey key;
  private final long accessMinutes;
  private final long refreshDays;

  public PatientTokenService(JdbcTemplate jdbc, @Value("${hospital.security.jwt-secret}") String secret,
      @Value("${hospital.patient.access-token-minutes:15}") long accessMinutes,
      @Value("${hospital.patient.refresh-token-days:7}") long refreshDays) {
    this.jdbc = jdbc;
    this.accessMinutes = accessMinutes;
    this.refreshDays = refreshDays;
    byte[] bytes = Base64.getDecoder().decode(secret);
    if (bytes.length < 32) throw new IllegalStateException("JWT secret must contain at least 32 bytes");
    this.key = Keys.hmacShaKeyFor(bytes);
  }

  public TokenPair issue(long patientId, String client, String ip, String userAgent) {
    PatientRow patient = patient(patientId);
    if (patient.status != 1) throw new BusinessException(ErrorCode.PATIENT_DISABLED);
    String access = Jwts.builder().id(java.util.UUID.randomUUID().toString()).issuer("hospital-platform")
        .subject(Long.toString(patient.id)).claim("subjectType", "PATIENT").claim("client", client)
        .claim("type", "access").claim("ver", patient.tokenVersion).audience().add(AUDIENCE).and()
        .issuedAt(new Date()).expiration(Date.from(Instant.now().plus(accessMinutes, ChronoUnit.MINUTES))).signWith(key).compact();
    String refresh = random();
    jdbc.update("insert into patient_refresh_token(patient_id,token_hash,client_type,audience,token_version,expires_at,ip_address,user_agent) values(?,?,?,?,?,date_add(now(3), interval ? day),?,?)",
        patientId, sha256(refresh), client, AUDIENCE, patient.tokenVersion, refreshDays, ip, userAgent);
    return new TokenPair(access, refresh, accessMinutes * 60);
  }

  public TokenPair refresh(String raw, String client, String ip, String userAgent) {
    String tokenHash = sha256(raw);
    var rows = jdbc.queryForList("select * from patient_refresh_token where token_hash=? and revoked_at is null and expires_at>now(3)", tokenHash);
    if (rows.isEmpty() || !client.equals(rows.getFirst().get("client_type"))) throw new BusinessException(ErrorCode.AUTH_004);
    var row = rows.getFirst();
    long patientId = ((Number) row.get("patient_id")).longValue();
    PatientRow patient = patient(patientId);
    if (patient.status != 1 || patient.tokenVersion != ((Number) row.get("token_version")).intValue()) throw new BusinessException(ErrorCode.AUTH_004);
    jdbc.update("update patient_refresh_token set revoked_at=now(3),replaced_by_hash=? where token_hash=?", "ROTATED", tokenHash);
    return issue(patientId, client, ip, userAgent);
  }

  /**
   * A patient logout must invalidate the access token immediately as well as the refresh token.
   * Patient access JWTs carry the patient's token version, so incrementing it is the atomic
   * server-side revocation fence for every already-issued access token of this patient.
   */
  @Transactional
  public void revoke(String raw) {
    String hash = sha256(raw);
    var rows = jdbc.queryForList("select patient_id from patient_refresh_token where token_hash=? and revoked_at is null", hash);
    if (rows.isEmpty()) return;
    long patientId = ((Number) rows.getFirst().get("patient_id")).longValue();
    jdbc.update("update patient set token_version=token_version+1 where id=? and deleted=0", patientId);
    jdbc.update("update patient_refresh_token set revoked_at=now(3) where patient_id=? and revoked_at is null", patientId);
  }
  public void revokeAll(long patientId) { jdbc.update("update patient_refresh_token set revoked_at=now(3) where patient_id=? and revoked_at is null", patientId); }

  public AuthenticatedPatient authenticate(String access) {
    try {
      Claims c = Jwts.parser().verifyWith(key).requireIssuer("hospital-platform").build().parseSignedClaims(access).getPayload();
      if (!"access".equals(c.get("type", String.class)) || !"PATIENT".equals(c.get("subjectType", String.class))
          || !c.getAudience().contains(AUDIENCE) || c.getId() == null) throw new BusinessException(ErrorCode.AUTH_003);
      long patientId = Long.parseLong(c.getSubject());
      PatientRow patient = patient(patientId);
      int version = ((Number) c.get("ver")).intValue();
      if (patient.status != 1 || patient.tokenVersion != version) throw new BusinessException(ErrorCode.AUTH_003);
      return new AuthenticatedPatient(patientId, version, c.get("client", String.class));
    } catch (BusinessException e) { throw e; }
    catch (Exception e) { throw new BusinessException(ErrorCode.AUTH_003); }
  }

  private PatientRow patient(long id) {
    return jdbc.query("select id,status,token_version from patient where id=? and deleted=0", (rs, row) -> new PatientRow(rs.getLong(1), rs.getInt(2), rs.getInt(3)), id)
        .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));
  }
  private String random() { byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
  private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  private record PatientRow(long id, int status, int tokenVersion) { }
  public record TokenPair(String accessToken, String refreshToken, long expiresIn) { }
}
