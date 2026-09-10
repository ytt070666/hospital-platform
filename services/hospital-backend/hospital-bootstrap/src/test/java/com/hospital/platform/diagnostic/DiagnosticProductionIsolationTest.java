package com.hospital.platform.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospital.platform.bootstrap.HospitalApplication;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production profile must fail closed and must not activate the diagnostic test adapter. */
@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(classes=HospitalApplication.class, properties={"hospital.payment.worker-enabled=false","hospital.bootstrap.admin-username=","spring.rabbitmq.listener.simple.auto-startup=false"})
class DiagnosticProductionIsolationTest {
  @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4");
  @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
  static final String key=key();

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
    r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);
    r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
    r.add("hospital.security.jwt-secret",()->key);r.add("hospital.patient.encryption-key",()->key);r.add("hospital.patient.hash-key",()->key);r.add("hospital.payment.test-signing-key",()->key);r.add("hospital.minio.endpoint",()->"http://localhost:9000");r.add("hospital.minio.access-key",()->"test-access");r.add("hospital.minio.secret-key",()->"test-secret");
  }

  @Autowired DiagnosticService diagnostics;

  @Test void productionContextDisablesTestProviderAndImagingExecutionFailsClosed(){
    assertThat(ReflectionTestUtils.getField(diagnostics,"testProviderEnabled")).isEqualTo(false);
    assertThatThrownBy(()->diagnostics.acquireStudy(1L,1L)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.errorCode()).isEqualTo(ErrorCode.CLINICAL_INTEGRATION_NOT_CONFIGURED));
  }

  static String key(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
}
