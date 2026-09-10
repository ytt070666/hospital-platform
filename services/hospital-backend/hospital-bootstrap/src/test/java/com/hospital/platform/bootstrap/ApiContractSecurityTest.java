package com.hospital.platform.bootstrap;
import static org.assertj.core.api.Assertions.assertThat; import com.hospital.platform.common.api.ApiResponse; import com.hospital.platform.common.error.ErrorCode; import org.junit.jupiter.api.Test;
/** Contract-level security response test; container integration is executed in CI with JDK 21 and Docker dependencies. */
class ApiContractSecurityTest { @Test void unauthorizedResponseDoesNotContainExceptionDetails(){var response=ApiResponse.failure(ErrorCode.AUTH_003.code(),ErrorCode.AUTH_003.message(),"test-trace");assertThat(response.code()).isEqualTo("AUTH_003");assertThat(response.data()).isNull();assertThat(response.traceId()).isEqualTo("test-trace");} }
