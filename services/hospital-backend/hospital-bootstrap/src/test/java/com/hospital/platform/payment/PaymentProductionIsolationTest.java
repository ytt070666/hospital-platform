package com.hospital.platform.payment;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class PaymentProductionIsolationTest {
  @Test void prodAndMixedProdProfilesHaveNoSimulatorBeanOrEndpoint(){
    for(String[] profiles:List.of(new String[]{"prod"},new String[]{"prod","dev"},new String[]{"prod","test"})){
      try(var context=new AnnotationConfigApplicationContext()){
        context.getEnvironment().setActiveProfiles(profiles);
        context.register(TestPaymentProvider.class,TestPaymentController.class);context.refresh();
        assertThat(context.getBeansOfType(TestPaymentProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(TestPaymentController.class)).isEmpty();
      }
    }
  }
  @Test void nonDevelopmentProfileHasNoSimulatorBeanOrEndpoint(){
    try(var context=new AnnotationConfigApplicationContext()){
      context.getEnvironment().setActiveProfiles("prod");
      context.register(TestPaymentProvider.class,TestPaymentController.class);context.refresh();
      assertThat(context.getBeansOfType(TestPaymentProvider.class)).isEmpty();assertThat(context.getBeansOfType(TestPaymentController.class)).isEmpty();
    }
  }
  @Test void productionRegistryNeverFallsBackAndMissingKeysFailClosed(){
    var environment=new MockEnvironment();environment.setActiveProfiles("prod","dev");
    var simulator=new TestPaymentProvider(null,PaymentCoreIntegrationTest.randomKey());
    var registry=new PaymentProviderRegistry(List.of(simulator),environment);
    for(String code:List.of("TEST","WECHAT","ALIPAY"))assertThatThrownBy(()->registry.require(code)).isInstanceOf(PaymentException.class).hasMessage("PAYMENT_PROVIDER_NOT_CONFIGURED");
    assertThat(new TestPaymentProvider(null,"").configured()).isFalse();
  }
}
