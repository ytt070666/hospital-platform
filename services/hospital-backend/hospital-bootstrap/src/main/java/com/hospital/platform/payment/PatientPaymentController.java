package com.hospital.platform.payment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.trace.TraceId;
import com.hospital.platform.common.api.PageResponse;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient")
public class PatientPaymentController {
  private final RegistrationOrderService orders;private final PaymentService payments;private final RefundService refunds;private final PaymentProviderRegistry providers;private final PaymentSupport support;
  public PatientPaymentController(RegistrationOrderService orders,PaymentService payments,RefundService refunds,PaymentProviderRegistry providers,PaymentSupport support) { this.orders=orders;this.payments=payments;this.refunds=refunds;this.providers=providers;this.support=support; }
  @GetMapping("/appointments/{id}/registration-order")
  public ApiResponse<PaymentViews.Registration> byAppointment(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(orders.byAppointment(patient(p),id)); }
  @PostMapping("/appointments/{id}/registration-order")
  public ApiResponse<PaymentViews.Registration> ensure(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(orders.ensure(patient(p),id)); }
  @GetMapping("/registration-orders/{id}")
  public ApiResponse<PaymentViews.Registration> registration(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(orders.detail(patient(p),id)); }
  @PostMapping("/registration-orders/{id}/payments")
  public ApiResponse<PaymentViews.Payment> create(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody PaymentService.Create body) { return ok(payments.create(patient(p),id,key,body)); }
  @GetMapping({"/payments/{id}","/payments/{id}/status"})
  public ApiResponse<PaymentViews.Payment> detail(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(payments.detail(patient(p),id)); }
  @PostMapping("/payments/{id}/query")
  public ApiResponse<PaymentViews.Payment> query(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(payments.query(patient(p),id)); }
  @GetMapping("/refunds/{id}") public ApiResponse<PaymentViews.Refund> refund(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id) { return ok(refunds.patientDetail(patient(p),id)); }
  @GetMapping("/payment/capabilities") public ApiResponse<?> capabilities(){return ok(Map.of("availableChannels",providers.availableCodes(),"testOnly",providers.availableCodes().contains("TEST")));}
  @GetMapping("/registration-orders") public ApiResponse<?> registrations(@AuthenticationPrincipal AuthenticatedPatient p,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize,@RequestParam(required=false)String status){return ok(registrationPage(patient(p),page,pageSize,status));}
  @GetMapping("/refunds") public ApiResponse<?> refunds(@AuthenticationPrincipal AuthenticatedPatient p,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize,@RequestParam(required=false)String status){long size=Math.min(100,Math.max(1,pageSize)),current=Math.max(1,page);String where="patient_id=?"+(status==null||status.isBlank()?"":" and status=?");Object[] args=status==null||status.isBlank()?new Object[]{patient(p)}:new Object[]{patient(p),status};Long total=support.db.queryForObject("select count(*) from refund_order where "+where,Long.class,args);return ok(new PageResponse<>(current,size,total==null?0:total,support.db.queryForList("select * from refund_order where "+where+" order by id desc limit ? offset ?",append(args,size,(current-1)*size)).stream().map(PaymentViews::refund).toList()));}
  private PageResponse<PaymentViews.Registration> registrationPage(long patient,long page,long pageSize,String status){long size=Math.min(100,Math.max(1,pageSize)),current=Math.max(1,page);String where="patient_id=? and deleted=0"+(status==null||status.isBlank()?"":" and status=?");Object[] args=status==null||status.isBlank()?new Object[]{patient}:new Object[]{patient,status};Long total=support.db.queryForObject("select count(*) from registration_order where "+where,Long.class,args);return new PageResponse<>(current,size,total==null?0:total,support.db.queryForList("select * from registration_order where "+where+" order by id desc limit ? offset ?",append(args,size,(current-1)*size)).stream().map(PaymentViews::registration).toList());}
  private static Object[] append(Object[] source,Object...more){Object[] result=java.util.Arrays.copyOf(source,source.length+more.length);System.arraycopy(more,0,result,source.length,more.length);return result;}
  static long patient(AuthenticatedPatient p) { if(p==null) throw new PaymentException("FORBIDDEN",403);return p.patientId(); }
  static <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data,TraceId.get()); }
}
