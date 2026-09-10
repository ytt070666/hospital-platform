package com.hospital.platform.appointment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.api.PageResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.trace.TraceId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/patient/appointments") public class PatientAppointmentController {
  private final AppointmentService service; private final com.hospital.platform.payment.RegistrationOrderService orders;
  public PatientAppointmentController(AppointmentService service,com.hospital.platform.payment.RegistrationOrderService orders){this.service=service;this.orders=orders;}
  @PostMapping("/holds") public ApiResponse<AppointmentService.View> hold(@AuthenticationPrincipal AuthenticatedPatient p,@RequestHeader("Idempotency-Key") String key,@RequestBody AppointmentService.HoldRequest r){AuthenticatedPatient patient=required(p);return ok(service.hold(patient.patientId(),r,key,patient.clientType()));}
  @GetMapping("/{id}") public ApiResponse<AppointmentService.View> detail(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id){return ok(service.detail(id(p),id));}
  @GetMapping public ApiResponse<PageResponse<AppointmentService.View>> list(@AuthenticationPrincipal AuthenticatedPatient p,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize){return ok(service.list(id(p),status,page,pageSize));}
  @PostMapping("/{id}/confirm") public ApiResponse<AppointmentService.View> confirm(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id){long patient=id(p);var result=service.confirm(patient,id);orders.ensure(patient,id);return ok(result);}
  @PostMapping("/{id}/cancel") public ApiResponse<AppointmentService.View> cancel(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id,@RequestBody(required=false)CancelRequest r){return ok(service.cancel(id(p),id,r==null?null:r.reasonCode(),r==null?null:r.reason()));}
  private AuthenticatedPatient required(AuthenticatedPatient p){if(p==null)throw new BusinessException(ErrorCode.FORBIDDEN);return p;} private long id(AuthenticatedPatient p){return required(p).patientId();} private static <T> ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.get());}
  public record CancelRequest(String reasonCode,String reason){}
}
