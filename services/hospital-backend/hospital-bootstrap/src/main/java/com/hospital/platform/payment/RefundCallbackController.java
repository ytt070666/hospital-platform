package com.hospital.platform.payment;
import com.hospital.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
@RestController public class RefundCallbackController {
  private final RefundCallbackService callbacks; public RefundCallbackController(RefundCallbackService callbacks){this.callbacks=callbacks;}
  @PostMapping("/api/v1/refund/callback/{provider}") public ApiResponse<String> callback(@PathVariable String provider,HttpServletRequest request)throws IOException {if(request.getContentLengthLong()>16384)throw new PaymentException("CALLBACK_TOO_LARGE",413);byte[] raw=request.getInputStream().readNBytes(16385);callbacks.accept(provider,raw,new PaymentProvider.Signature(header(request,"Wechatpay-Timestamp","X-Payment-Timestamp"),header(request,"Wechatpay-Nonce","X-Payment-Nonce"),header(request,"Wechatpay-Signature","X-Payment-Signature"),header(request,"Wechatpay-Serial","X-Payment-Key-Id")));return PatientPaymentController.ok("ACK");}
  private static String header(HttpServletRequest request,String preferred,String fallback){String value=request.getHeader(preferred);return value==null?request.getHeader(fallback):value;}
}
