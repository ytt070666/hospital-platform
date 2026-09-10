package com.hospital.platform.payment;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

/** Loads mounted production secrets only when an adapter is actually configured. */
final class ProviderCrypto {
  private ProviderCrypto() {}
  static final Duration CONNECT_TIMEOUT=Duration.ofSeconds(5), REQUEST_TIMEOUT=Duration.ofSeconds(12);
  static PrivateKey privateKey(String path) {
    try { return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pem(Files.readString(Path.of(path))))); }
    catch(Exception e){throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);}
  }
  static PublicKey publicKey(String path) {
    try {
      byte[] body=Files.readAllBytes(Path.of(path));
      try { return ((X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(body))).getPublicKey(); }
      catch(Exception ignored){return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(new String(body,StandardCharsets.UTF_8))));}
    } catch(Exception e){throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);}
  }
  static String certificateSerial(String path) {
    try { return ((X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(Files.newInputStream(Path.of(path)))).getSerialNumber().toString(16).toUpperCase(); }
    catch(Exception e){throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);}
  }
  static boolean rsaVerify(PublicKey key,String algorithm,byte[] data,String base64) {
    try { Signature verifier=Signature.getInstance(algorithm);verifier.initVerify(key);verifier.update(data);return verifier.verify(Base64.getDecoder().decode(base64)); }
    catch(Exception e){return false;}
  }
  static String rsaSign(PrivateKey key,String algorithm,byte[] data) {
    try { Signature signer=Signature.getInstance(algorithm);signer.initSign(key);signer.update(data);return Base64.getEncoder().encodeToString(signer.sign()); }
    catch(Exception e){throw new PaymentException("PROVIDER_SIGNING_UNAVAILABLE",503);}
  }
  static byte[] pem(String value) {
    String encoded=value.replaceAll("-----BEGIN [A-Z ]+-----|-----END [A-Z ]+-----|\\s","");return Base64.getDecoder().decode(encoded);
  }
}
