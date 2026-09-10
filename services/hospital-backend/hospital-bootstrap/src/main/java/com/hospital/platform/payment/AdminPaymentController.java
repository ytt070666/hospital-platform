package com.hospital.platform.payment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPaymentController {
  private final FeeRuleService fees;private final PaymentAdminQueryService queries;private final PaymentSupport s;private final FinanceDashboardService finance;
  public AdminPaymentController(FeeRuleService fees,PaymentAdminQueryService queries,PaymentSupport s,FinanceDashboardService finance) { this.fees=fees;this.queries=queries;this.s=s;this.finance=finance; }
  @GetMapping("/finance/dashboard") @PreAuthorize("hasAuthority('hospital:finance:dashboard')")
  public ApiResponse<?> dashboard(@AuthenticationPrincipal AuthenticatedUser user){return PatientPaymentController.ok(finance.overview(user.id()));}
  @GetMapping("/fee-rules") @PreAuthorize("hasAuthority('hospital:fee:list')")
  public ApiResponse<?> listFees(@RequestParam(defaultValue="200") int limit) { return PatientPaymentController.ok(fees.list(limit)); }
  @PostMapping("/fee-rules") @PreAuthorize("hasAuthority('hospital:fee:create')")
  public ApiResponse<?> createFee(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody FeeRuleService.Input input) { return PatientPaymentController.ok(fees.save(user.id(),null,input)); }
  @PutMapping("/fee-rules/{id}") @PreAuthorize("hasAuthority('hospital:fee:update')")
  public ApiResponse<?> updateFee(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody FeeRuleService.Input input) { return PatientPaymentController.ok(fees.save(user.id(),id,input)); }
  @GetMapping("/registration-orders") @PreAuthorize("hasAnyAuthority('hospital:registration-order:list','hospital:payment:list')")
  public ApiResponse<?> registrations(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize,@RequestParam(required=false)String status) { return PatientPaymentController.ok(queries.list(user.id(),false,page,pageSize,status)); }
  @GetMapping("/payment-orders") @PreAuthorize("hasAuthority('hospital:payment:list')")
  public ApiResponse<?> payments(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize,@RequestParam(required=false)String status) { return PatientPaymentController.ok(queries.list(user.id(),true,page,pageSize,status)); }
  @GetMapping("/registration-orders/{id}") @PreAuthorize("hasAnyAuthority('hospital:registration-order:view','hospital:payment:view')")
  public ApiResponse<?> registration(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) { return PatientPaymentController.ok(queries.detail(user.id(),false,id)); }
  @GetMapping("/payment-orders/{id}") @PreAuthorize("hasAuthority('hospital:payment:view')")
  public ApiResponse<?> payment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) { return PatientPaymentController.ok(queries.detail(user.id(),true,id)); }
  public record ClinicPolicy(boolean paymentRequired,long version) {}
  @GetMapping("/fee-rules/clinic-policies") @PreAuthorize("hasAuthority('hospital:fee:list')")
  public ApiResponse<?> clinics() { return PatientPaymentController.ok(s.db.queryForList("select id,name,payment_required paymentRequired,version from clinic_type where deleted=0 order by id")); }
  @PutMapping("/fee-rules/clinic-policies/{id}") @PreAuthorize("hasAuthority('hospital:fee:update')")
  public ApiResponse<?> policy(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody ClinicPolicy policy) {
    s.tx.executeWithoutResult(ignored->{
      var rows=s.db.queryForList("select id,payment_required,version from clinic_type where id=? and deleted=0 for update",id);
      if(rows.isEmpty()) throw new PaymentException("CLINIC_NOT_FOUND",404);
      if(s.db.update("update clinic_type set payment_required=?,version=version+1 where id=? and version=?",policy.paymentRequired(),id,policy.version())!=1) throw new PaymentException("FEE_RULE_VERSION_CONFLICT",409);
      s.audit("CLINIC_PRICING_POLICY_UPDATED","CLINIC_TYPE",id,"ADMIN",user.id(),"OK",rows.getFirst(),policy);
    });return PatientPaymentController.ok("OK");
  }
}
