package com.hospital.platform.payment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.security.AuthenticatedPatient;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Development simulator requires patient ownership and returns signed test events, never a signing key. */
@RestController
@Profile("!prod & (dev | test)")
@RequestMapping("/api/v1/patient/payments")
public class TestPaymentController {
  private final PaymentService payments;private final RefundService refunds;private final TestPaymentProvider provider;
  public TestPaymentController(PaymentService payments,RefundService refunds,TestPaymentProvider provider) { this.payments=payments;this.refunds=refunds;this.provider=provider; }
  public record Scenario(String scenario) {}
  @PostMapping("/{id}/test-provider")
  public ApiResponse<TestPaymentProvider.SignedCallback> simulate(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id,@RequestBody Scenario input) {
    var payment=payments.detail(PatientPaymentController.patient(p),id);
    if(!"TEST".equals(payment.provider())) throw new PaymentException("INVALID_TEST_PROVIDER",400);
    String scenario=input.scenario();
    if(scenario==null||!java.util.Set.of("SUCCESS","FAIL","PENDING","TIMEOUT","DELAYED_CALLBACK","DUPLICATE_CALLBACK","INVALID_SIGNATURE","WRONG_AMOUNT").contains(scenario)) throw new PaymentException("INVALID_TEST_SCENARIO",400);
    String target=switch(scenario){case "FAIL"->"FAILED";case "PENDING","TIMEOUT"->scenario;default->"SUCCESS";};
    var result=provider.simulate(payment.paymentNo(),target);
    var signed=provider.signed(new PaymentProvider.Callback(PaymentSupport.number("EV"),result.paymentNo(),result.transactionId(),"WRONG_AMOUNT".equals(scenario)?1:result.amountCent(),result.currency(),result.status()));
    if("INVALID_SIGNATURE".equals(scenario)) signed=new TestPaymentProvider.SignedCallback(signed.body(),new PaymentProvider.Signature(signed.headers().timestamp(),signed.headers().nonce(),"invalid"));
    return PatientPaymentController.ok(signed);
  }
  @PostMapping("/refunds/{id}/test-provider") public ApiResponse<TestPaymentProvider.SignedCallback> refund(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id,@RequestBody Scenario input) {
    var order=refunds.patientDetail(PatientPaymentController.patient(p),id);String scenario=input.scenario();if(scenario==null||!java.util.Set.of("REFUND_SUCCESS","REFUND_FAILED","REFUND_PENDING","INVALID_REFUND_SIGNATURE","WRONG_REFUND_AMOUNT").contains(scenario))throw new PaymentException("INVALID_TEST_SCENARIO",400);
    String status="REFUND_SUCCESS".equals(scenario)||"INVALID_REFUND_SIGNATURE".equals(scenario)||"WRONG_REFUND_AMOUNT".equals(scenario)?"SUCCESS":"REFUND_FAILED".equals(scenario)?"FAILED":"PENDING";var r=provider.simulateRefund(order.refundNo(),status);var signed=provider.signedRefund(new PaymentProvider.RefundCallback(PaymentSupport.number("REV"),r.refundNo(),r.providerRefundNo(),r.paymentNo(),"WRONG_REFUND_AMOUNT".equals(scenario)?1:r.amountCent(),r.currency(),r.status()));if("INVALID_REFUND_SIGNATURE".equals(scenario))signed=new TestPaymentProvider.SignedCallback(signed.body(),new PaymentProvider.Signature(signed.headers().timestamp(),signed.headers().nonce(),"invalid"));return PatientPaymentController.ok(signed);
  }
}
