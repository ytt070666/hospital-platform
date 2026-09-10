package com.hospital.platform.payment;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fixture-only provider contracts: no merchant credentials and no provider network access. */
class ProductionProviderContractTest {
  @Test void alipayNotifyVerifiesRawFormSignatureAndParsesMinorAmount() throws Exception {
    var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
    var privateFile=Files.createTempFile("alipay-test-",".pem");var publicFile=Files.createTempFile("alipay-test-",".pem");
    Files.writeString(privateFile,pem("PRIVATE KEY",pair.getPrivate().getEncoded()));Files.writeString(publicFile,pem("PUBLIC KEY",pair.getPublic().getEncoded()));
    try {
      var provider=new AlipayPaymentProvider(null,"fixture-app",privateFile.toString(),publicFile.toString(),"https://hospital.example/notify","https://openapi.alipay.com/gateway.do");
      Map<String,String> fields=new LinkedHashMap<>();fields.put("app_id","fixture-app");fields.put("notify_id","fixture-event-1");fields.put("notify_time",DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now(ZoneOffset.UTC)));fields.put("out_trade_no","POfixture1");fields.put("trade_no","ALITXfixture1");fields.put("total_amount","10.50");fields.put("trade_status","TRADE_SUCCESS");
      String canonical=fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+"&"+b).orElseThrow();fields.put("sign",ProviderCrypto.rsaSign(pair.getPrivate(),"SHA256withRSA",canonical.getBytes(StandardCharsets.UTF_8)));
      byte[] raw=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElseThrow().getBytes(StandardCharsets.UTF_8);
      assertThat(provider.verifyCallback(raw,new PaymentProvider.Signature(null,null,null))).isTrue();
      var callback=provider.parseCallback(raw);assertThat(callback.amountCent()).isEqualTo(1050);assertThat(callback.status()).isEqualTo("SUCCESS");
      var wechat=new WeChatPayProvider(null,"fixture-mch","fixture-app","12345678901234567890123456789012",privateFile.toString(),"fixture-serial",publicFile.toString(),"https://hospital.example/wechat","https://hospital.example/wechat-refund");
      byte[] wechatRaw="{\"id\":\"fixture-event\"}".getBytes(StandardCharsets.UTF_8);String timestamp=Long.toString(Instant.now().getEpochSecond()),nonce="fixture-nonce-123456";String signed=ProviderCrypto.rsaSign(pair.getPrivate(),"SHA256withRSA",(timestamp+"\n"+nonce+"\n"+new String(wechatRaw,StandardCharsets.UTF_8)+"\n").getBytes(StandardCharsets.UTF_8));
      assertThat(wechat.verifyCallback(wechatRaw,new PaymentProvider.Signature(timestamp,nonce,signed))).isTrue();
    } finally {Files.deleteIfExists(privateFile);Files.deleteIfExists(publicFile);}
  }
  @Test void productionAdaptersFailClosedWithoutExternalSecrets(){
    var wechat=new WeChatPayProvider(null,"","","","","","","","");var alipay=new AlipayPaymentProvider(null,"","","","","https://openapi.alipay.com/gateway.do");
    assertThat(wechat.configured()).isFalse();assertThat(alipay.configured()).isFalse();assertThat(wechat.verifyCallback("{}".getBytes(StandardCharsets.UTF_8),new PaymentProvider.Signature("0","n","x"))).isFalse();assertThat(alipay.verifyCallback("".getBytes(StandardCharsets.UTF_8),new PaymentProvider.Signature(null,null,null))).isFalse();
  }
  private static String pem(String label,byte[] bytes){return "-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)+"\n-----END "+label+"-----\n";}
}
