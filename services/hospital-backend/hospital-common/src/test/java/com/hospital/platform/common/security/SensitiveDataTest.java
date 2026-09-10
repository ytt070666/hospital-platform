package com.hospital.platform.common.security;
import static org.assertj.core.api.Assertions.assertThat; import org.junit.jupiter.api.Test;
class SensitiveDataTest { @Test void masksPhoneWithoutLeakingMiddleDigits(){assertThat(SensitiveData.maskPhone("13812345678")).isEqualTo("138****5678");} @Test void masksIdCardWithoutLeakingIdentity(){assertThat(SensitiveData.maskIdCard("320101199001011234")).isEqualTo("32********1234");} }
