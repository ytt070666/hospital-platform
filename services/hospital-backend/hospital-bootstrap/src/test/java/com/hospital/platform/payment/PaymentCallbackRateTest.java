package com.hospital.platform.payment;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentCallbackRateTest {
  @Test void unauthenticatedGarbageIsBoundedBeforeProviderParsing(){
    var registry=new PaymentProviderRegistry(List.of(),new MockEnvironment());
    var callbacks=new PaymentCallbackService(null,null,registry,100);
    for(int i=0;i<100;i++)assertThatThrownBy(()->callbacks.accept("UNCONFIGURED",new byte[0],null)).hasMessage("PAYMENT_PROVIDER_NOT_CONFIGURED");
    assertThatThrownBy(()->callbacks.accept("UNCONFIGURED",new byte[0],null)).hasMessage("CALLBACK_RATE_LIMITED");
  }
}
