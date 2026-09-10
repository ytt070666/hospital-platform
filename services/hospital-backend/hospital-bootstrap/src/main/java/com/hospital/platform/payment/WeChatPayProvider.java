package com.hospital.platform.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** WeChat Pay v3 adapter. It is fail-closed and never uses TEST or a caller-provided URL. */
@Component
public class WeChatPayProvider implements PaymentProvider {
  private static final String API="https://api.mch.weixin.qq.com";
  private final PaymentSupport s; private final String mchId,appId,apiV3Key,privateKeyPath,serial,platformCertPath,notifyUrl,refundNotifyUrl;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(ProviderCrypto.CONNECT_TIMEOUT).build();
  public WeChatPayProvider(PaymentSupport s,
      @Value("${hospital.payment.wechat.mch-id:}") String mchId,@Value("${hospital.payment.wechat.app-id:}") String appId,
      @Value("${hospital.payment.wechat.api-v3-key:}") String apiV3Key,@Value("${hospital.payment.wechat.private-key-path:}") String privateKeyPath,
      @Value("${hospital.payment.wechat.cert-serial-no:}") String serial,@Value("${hospital.payment.wechat.platform-cert-path:}") String platformCertPath,
      @Value("${hospital.payment.wechat.notify-url:}") String notifyUrl,@Value("${hospital.payment.wechat.refund-notify-url:}") String refundNotifyUrl) {
    this.s=s;this.mchId=mchId;this.appId=appId;this.apiV3Key=apiV3Key;this.privateKeyPath=privateKeyPath;this.serial=serial;this.platformCertPath=platformCertPath;this.notifyUrl=notifyUrl;this.refundNotifyUrl=refundNotifyUrl;
  }
  @Override public String code(){return "WECHAT";}
  @Override public boolean configured(){return nonblank(mchId,appId,apiV3Key,privateKeyPath,serial,platformCertPath,notifyUrl,refundNotifyUrl)&&apiV3Key.length()==32;}
  @Override public Result createPayment(String paymentNo,Money money){
    Map<String,Object> body=new LinkedHashMap<>();body.put("appid",appId);body.put("mchid",mchId);body.put("description","医院挂号费");body.put("out_trade_no",paymentNo);body.put("notify_url",notifyUrl);body.put("amount",Map.of("total",money.amountMinor(),"currency",money.currency()));
    JsonNode n=call("POST","/v3/pay/transactions/native",s.json(body));return new Result(paymentNo,n.path("code_url").asText(paymentNo),null,money.amountMinor(),money.currency(),"PENDING");
  }
  @Override public Result queryPayment(String paymentNo){return payment(call("GET","/v3/pay/transactions/out-trade-no/"+paymentNo+"?mchid="+mchId,""));}
  @Override public Result closePayment(String paymentNo){Result before=queryPayment(paymentNo);call("POST","/v3/pay/transactions/out-trade-no/"+paymentNo+"/close",s.json(Map.of("mchid",mchId)));return new Result(paymentNo,paymentNo,null,before.amountCent(),before.currency(),"CLOSED");}
  @Override public RefundResult createRefund(String refundNo,String paymentNo,Money money){
    JsonNode n=call("POST","/v3/refund/domestic/refunds",s.json(Map.of("out_trade_no",paymentNo,"out_refund_no",refundNo,"notify_url",refundNotifyUrl,"amount",Map.of("refund",money.amountMinor(),"total",money.amountMinor(),"currency",money.currency()))));
    return refund(n,refundNo,paymentNo,money);
  }
  @Override public RefundResult queryRefund(String refundNo){JsonNode n=call("GET","/v3/refund/domestic/refunds/"+refundNo,"");long amount=n.path("amount").path("refund").asLong();return refund(n,refundNo,n.path("out_trade_no").asText(),Money.ofCent(amount,n.path("amount").path("currency").asText("CNY")));}
  @Override public boolean verifyCallback(byte[] raw,Signature signature){return verify(raw,signature);}
  @Override public boolean verifyRefundCallback(byte[] raw,Signature signature){return verify(raw,signature);}
  @Override public Callback parseCallback(byte[] raw){
    try {JsonNode envelope=s.json.readTree(raw),data=decrypt(envelope.path("resource"));return new Callback(envelope.path("id").asText(),data.path("out_trade_no").asText(),data.path("transaction_id").asText(null),data.path("amount").path("total").asLong(),data.path("amount").path("currency").asText(),paymentStatus(data.path("trade_state").asText()));}
    catch(PaymentException e){throw e;}catch(Exception e){throw new PaymentException("INVALID_CALLBACK_BODY",400);}
  }
  @Override public RefundCallback parseRefundCallback(byte[] raw){
    try {JsonNode envelope=s.json.readTree(raw),data=decrypt(envelope.path("resource"));return new RefundCallback(envelope.path("id").asText(),data.path("out_refund_no").asText(),data.path("refund_id").asText(),data.path("out_trade_no").asText(),data.path("amount").path("refund").asLong(),data.path("amount").path("currency").asText(),refundStatus(data.path("refund_status").asText()));}
    catch(PaymentException e){throw e;}catch(Exception e){throw new PaymentException("INVALID_REFUND_CALLBACK_BODY",400);}
  }
  private boolean verify(byte[] raw,Signature signature){
    try {if(!configured()||signature==null||!signature.timestamp().matches("[0-9]{10}")||signature.nonce()==null||signature.signature()==null)return false;long at=Long.parseLong(signature.timestamp());if(Math.abs(Instant.now().getEpochSecond()-at)>300)return false;if(signature.keyId()!=null&&!signature.keyId().equalsIgnoreCase(ProviderCrypto.certificateSerial(platformCertPath)))return false;String message=signature.timestamp()+"\n"+signature.nonce()+"\n"+new String(raw,StandardCharsets.UTF_8)+"\n";return ProviderCrypto.rsaVerify(ProviderCrypto.publicKey(platformCertPath),"SHA256withRSA",message.getBytes(StandardCharsets.UTF_8),signature.signature());}catch(Exception e){return false;}
  }
  private JsonNode decrypt(JsonNode resource){
    try {String associated=resource.path("associated_data").asText(),nonce=resource.path("nonce").asText(),ciphertext=resource.path("ciphertext").asText();if(nonce.length()!=12||ciphertext.isBlank())throw new IllegalArgumentException();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));c.updateAAD(associated.getBytes(StandardCharsets.UTF_8));return s.json.readTree(c.doFinal(Base64.getDecoder().decode(ciphertext)));}catch(Exception e){throw new PaymentException("PROVIDER_INVALID_RESPONSE",400);}
  }
  private JsonNode call(String method,String path,String body){
    if(!configured())throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);try {String payload=body==null?"":body,nonce=PaymentSupport.number("n"),timestamp=Long.toString(Instant.now().getEpochSecond()),message=method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+payload+"\n";PrivateKey key=ProviderCrypto.privateKey(privateKeyPath);String authorization="WECHATPAY2-SHA256-RSA2048 mchid=\""+mchId+"\",nonce_str=\""+nonce+"\",timestamp=\""+timestamp+"\",serial_no=\""+serial+"\",signature=\""+ProviderCrypto.rsaSign(key,"SHA256withRSA",message.getBytes(StandardCharsets.UTF_8))+"\"";HttpRequest request=HttpRequest.newBuilder(URI.create(API+path)).timeout(ProviderCrypto.REQUEST_TIMEOUT).header("Authorization",authorization).header("Accept","application/json").header("Content-Type","application/json").method(method,payload.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(payload)).build();HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw mapped(response.statusCode());return s.json.readTree(response.body());}catch(PaymentException e){throw e;}catch(java.net.http.HttpTimeoutException e){throw new PaymentException("PROVIDER_TIMEOUT",503);}catch(java.io.IOException e){throw new PaymentException("PROVIDER_UNAVAILABLE",503);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new PaymentException("PROVIDER_TIMEOUT",503);}catch(Exception e){throw new PaymentException("PROVIDER_INVALID_RESPONSE",502);}}
  private static PaymentException mapped(int status){if(status==404)return new PaymentException("PROVIDER_ORDER_NOT_FOUND",404);if(status==409)return new PaymentException("PAYMENT_ALREADY_PAID",409);if(status==400||status==403)return new PaymentException("PROVIDER_INVALID_RESPONSE",502);return new PaymentException("PROVIDER_UNAVAILABLE",503);}
  private static Result payment(JsonNode n){JsonNode amount=n.path("amount");return new Result(n.path("out_trade_no").asText(),n.path("transaction_id").asText(n.path("out_trade_no").asText()),n.path("transaction_id").asText(null),amount.path("total").asLong(),amount.path("currency").asText(),paymentStatus(n.path("trade_state").asText()));}
  private static RefundResult refund(JsonNode n,String refundNo,String paymentNo,Money fallback){JsonNode amount=n.path("amount");return new RefundResult(refundNo,n.path("refund_id").asText(refundNo),n.path("out_trade_no").asText(paymentNo),amount.path("refund").asLong(fallback.amountMinor()),amount.path("currency").asText(fallback.currency()),refundStatus(n.path("status").asText()));}
  private static String paymentStatus(String state){return switch(state){case "SUCCESS"->"SUCCESS";case "PAYERROR"->"FAILED";case "CLOSED","REVOKED"->"CLOSED";default->"PENDING";};}
  private static String refundStatus(String state){return switch(state){case "SUCCESS"->"SUCCESS";case "ABNORMAL"->"FAILED";case "CLOSED"->"CLOSED";default->"PENDING";};}
  private static boolean nonblank(String... values){for(String value:values)if(value==null||value.isBlank())return false;return true;}
}
