package com.hospital.platform.payment;

import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {
  private final List<PaymentProvider> providers;
  private final Environment env;
  public PaymentProviderRegistry(List<PaymentProvider> providers,Environment env) { this.providers=providers;this.env=env; }
  public PaymentProvider require(String code) {
    if("TEST".equals(code) && (env.acceptsProfiles(Profiles.of("prod")) || !env.acceptsProfiles(Profiles.of("dev","test")) ))
      throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);
    return providers.stream().filter(p->p.code().equals(code)&&p.configured()).findFirst()
      .orElseThrow(()->new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503));
  }
  public List<String> availableCodes() {
    return providers.stream()
      .filter(p->!"TEST".equals(p.code()) || (!env.acceptsProfiles(Profiles.of("prod")) && env.acceptsProfiles(Profiles.of("dev","test"))))
      .filter(PaymentProvider::configured).map(PaymentProvider::code).toList();
  }
}
