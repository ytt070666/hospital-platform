package com.hospital.platform.patient;

import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM encryption and keyed search hashes; keys are never persisted or logged. */
@Component
public class PatientCrypto {
  private final byte[] encryptionKey;
  private final byte[] hashKey;
  public PatientCrypto(@Value("${hospital.patient.encryption-key:${hospital.security.jwt-secret:}}") String encryptionKey,
      @Value("${hospital.patient.hash-key:${hospital.security.jwt-secret:}}") String hashKey) {
    this.encryptionKey = aesKey(decode(encryptionKey));
    this.hashKey = decode(hashKey);
  }
  public String encrypt(String plain) {
    if (plain == null) return null;
    try {
      byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
      byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[nonce.length + ciphertext.length];
      System.arraycopy(nonce, 0, payload, 0, nonce.length); System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
      return "v1:" + Base64.getEncoder().encodeToString(payload);
    } catch (Exception e) { throw new IllegalStateException("patient encryption unavailable", e); }
  }
  public String decrypt(String encoded) {
    if (encoded == null) return null;
    try {
      byte[] payload = Base64.getDecoder().decode(encoded.substring(encoded.indexOf(':') + 1));
      byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, 12);
      byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) { throw new IllegalStateException("patient encryption unavailable", e); }
  }
  public String hmac(String normalized) {
    try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hashKey, "HmacSHA256")); return java.util.HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException("patient hash unavailable", e); }
  }
  private byte[] decode(String raw) {
    try { byte[] key = Base64.getDecoder().decode(raw); if (key.length < 32) throw new IllegalArgumentException(); return key; }
    catch (Exception e) { throw new BusinessException(ErrorCode.SYSTEM); }
  }
  /** Existing AES-sized keys remain unchanged; a longer development secret is deterministically reduced to AES-256. */
  private byte[] aesKey(byte[] decoded) {
    if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) return decoded;
    try { return MessageDigest.getInstance("SHA-256").digest(decoded); }
    catch (Exception e) { throw new IllegalStateException("patient encryption unavailable", e); }
  }
}
