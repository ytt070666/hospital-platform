package com.hospital.platform.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Alipay OpenAPI RSA2 adapter. Gateway and TLS policy are fixed by server configuration. */
@Component
public class AlipayPaymentProvider implements PaymentProvider {
  private static final String DEFAULT_GATEWAY="https://openapi.alipay.com/gateway.do";
  private final PaymentSupport s;private final String appId,privateKeyPath,publicKeyPath,notifyUrl,gateway;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(ProviderCrypto.CONNECT_TIMEOUT).build();
  public AlipayPaymentProvider(PaymentSupport s,@Value("${hospital.payment.alipay.app-id:}") String appId,@Value("${hospital.payment.alipay.private-key-path:}") String privateKeyPath,@Value("${hospital.payment.alipay.public-key-path:}") String publicKeyPath,@Value("${hospital.payment.alipay.notify-url:}") String notifyUrl,@Value("${hospital.payment.alipay.gateway:"+DEFAULT_GATEWAY+"}") String gateway){this.s=s;this.appId=appId;this.privateKeyPath=privateKeyPath;this.publicKeyPath=publicKeyPath;this.notifyUrl=notifyUrl;this.gateway=gateway;}
  @Override public String code(){return "ALIPAY";}
  @Override public boolean configured(){return nonblank(appId,privateKeyPath,publicKeyPath,notifyUrl)&&gateway.startsWith("https://");}
  @Override public Result createPayment(String paymentNo,Money money){JsonNode n=call("alipay.trade.precreate",Map.of("out_trade_no",paymentNo,"total_amount",major(money.amountMinor()),"subject","医院挂号费","notify_url",notifyUrl));ensure(n);return new Result(paymentNo,n.path("qr_code").asText(paymentNo),null,money.amountMinor(),money.currency(),"PENDING");}
  @Override public Result queryPayment(String paymentNo){JsonNode n=call("alipay.trade.query",Map.of("out_trade_no",paymentNo));ensure(n);return payment(n,paymentNo);}
  @Override public Result closePayment(String paymentNo){Result before=queryPayment(paymentNo);JsonNode n=call("alipay.trade.close",Map.of("out_trade_no",paymentNo));ensure(n);return new Result(paymentNo,paymentNo,null,before.amountCent(),before.currency(),"CLOSED");}
  @Override public RefundResult createRefund(String refundNo,String paymentNo,Money money){JsonNode n=call("alipay.trade.refund",Map.of("out_trade_no",paymentNo,"out_request_no",refundNo,"refund_amount",major(money.amountMinor())));ensure(n);return new RefundResult(refundNo,n.path("trade_no").asText(refundNo),paymentNo,money.amountMinor(),money.currency(),"SUCCESS");}
  @Override public RefundResult queryRefund(String refundNo){throw new PaymentException("REFUND_QUERY_REQUIRES_PAYMENT_REFERENCE",503);}
  @Override public boolean verifyCallback(byte[] raw,Signature ignored){return verifyForm(raw);}
  @Override public boolean verifyRefundCallback(byte[] raw,Signature ignored){return verifyForm(raw);}
  @Override public Callback parseCallback(byte[] raw){Map<String,String> p=form(raw);validateNotify(p);return new Callback(p.get("notify_id"),p.get("out_trade_no"),p.get("trade_no"),cent(p.get("total_amount")),"CNY",paymentStatus(p.get("trade_status")));}
  @Override public RefundCallback parseRefundCallback(byte[] raw){Map<String,String> p=form(raw);validateNotify(p);String refundNo=p.getOrDefault("out_biz_no",p.get("out_request_no"));return new RefundCallback(p.get("notify_id"),refundNo,p.getOrDefault("refund_id",refundNo),p.get("out_trade_no"),cent(p.getOrDefault("refund_fee",p.get("refund_amount"))),"CNY",refundStatus(p.get("refund_status")));}
  private JsonNode call(String method,Map<String,String> business){
    if(!configured())throw new PaymentException("PAYMENT_PROVIDER_NOT_CONFIGURED",503);try {Map<String,String> all=new LinkedHashMap<>();all.put("app_id",appId);all.put("method",method);all.put("format","JSON");all.put("charset","utf-8");all.put("sign_type","RSA2");all.put("timestamp",DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.now()));all.put("version","1.0");all.put("biz_content",s.json(business));String source=canonical(all);all.put("sign",ProviderCrypto.rsaSign(ProviderCrypto.privateKey(privateKeyPath),"SHA256withRSA",source.getBytes(StandardCharsets.UTF_8)));String requestBody=url(all);HttpRequest request=HttpRequest.newBuilder(URI.create(gateway)).timeout(ProviderCrypto.REQUEST_TIMEOUT).header("Content-Type","application/x-www-form-urlencoded;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new PaymentException("PROVIDER_UNAVAILABLE",503);JsonNode root=s.json.readTree(response.body());JsonNode payload=root.fields().next().getValue();return payload;}catch(PaymentException e){throw e;}catch(java.net.http.HttpTimeoutException e){throw new PaymentException("PROVIDER_TIMEOUT",503);}catch(java.io.IOException e){throw new PaymentException("PROVIDER_UNAVAILABLE",503);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new PaymentException("PROVIDER_TIMEOUT",503);}catch(Exception e){throw new PaymentException("PROVIDER_INVALID_RESPONSE",502);}}
  private boolean verifyForm(byte[] raw){try {if(!configured())return false;Map<String,String> values=form(raw);String signature=values.remove("sign");if(signature==null||!appId.equals(values.get("app_id")))return false;validateNotify(values);return ProviderCrypto.rsaVerify(ProviderCrypto.publicKey(publicKeyPath),"SHA256withRSA",canonical(values).getBytes(StandardCharsets.UTF_8),signature);}catch(Exception e){return false;}}
  private void validateNotify(Map<String,String> p){if(!appId.equals(p.get("app_id"))||blank(p.get("out_trade_no"))||blank(p.get("notify_id")))throw new PaymentException("PROVIDER_SIGNATURE_INVALID",401);String time=p.get("notify_time");if(time!=null){try{long age=Math.abs(Instant.now().getEpochSecond()-LocalDateTime.parse(time,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toEpochSecond(ZoneOffset.UTC));if(age>600)throw new PaymentException("PROVIDER_SIGNATURE_INVALID",401);}catch(PaymentException e){throw e;}catch(Exception e){throw new PaymentException("PROVIDER_SIGNATURE_INVALID",401);}}}
  private static Map<String,String> form(byte[] raw){Map<String,String> result=new LinkedHashMap<>();for(String pair:new String(raw,StandardCharsets.UTF_8).split("&")){String[] kv=pair.split("=",2);if(kv.length==2)result.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return result;}
  private static String canonical(Map<String,String> values){return values.entrySet().stream().filter(e->e.getValue()!=null&&!"sign".equals(e.getKey())&&!"sign_type".equals(e.getKey())).sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+"&"+b).orElse("");}
  private static String url(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElse("");}
  private static void ensure(JsonNode n){if(!"10000".equals(n.path("code").asText()))throw new PaymentException("PROVIDER_INVALID_RESPONSE",502);}
  private static Result payment(JsonNode n,String fallback){String trade=n.path("trade_status").asText();return new Result(fallback,n.path("trade_no").asText(fallback),n.path("trade_no").asText(null),cent(n.path("total_amount").asText("0")),"CNY",paymentStatus(trade));}
  private static String paymentStatus(String status){return switch(status){case "TRADE_SUCCESS","TRADE_FINISHED"->"SUCCESS";case "TRADE_CLOSED"->"CLOSED";case "WAIT_BUYER_PAY"->"PENDING";default->"FAILED";};}
  private static String refundStatus(String status){return "REFUND_SUCCESS".equals(status)||"SUCCESS".equals(status)?"SUCCESS":"FAILED";}
  private static long cent(String value){try{return new BigDecimal(value).movePointRight(2).longValueExact();}catch(Exception e){throw new PaymentException("PROVIDER_INVALID_RESPONSE",400);}}
  private static String major(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
  private static boolean blank(String value){return value==null||value.isBlank();}
  private static boolean nonblank(String... values){for(String value:values)if(blank(value))return false;return true;}
}
