package com.hospital.platform.visit;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Thin transport boundary; all state changes are delegated to the encounter domain service. */
@RestController public class OutpatientVisitController {
  private final OutpatientVisitService visits; public OutpatientVisitController(OutpatientVisitService visits){this.visits=visits;}
  @PostMapping("/api/v1/patient/appointments/{id}/check-in") public ApiResponse<OutpatientVisitService.QueueView> patientCheckin(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id){return ok(visits.checkInPatient(patient(p),id));}
  @GetMapping("/api/v1/patient/visits/queues") public ApiResponse<List<Map<String,Object>>> patientQueues(@AuthenticationPrincipal AuthenticatedPatient p){return ok(visits.patientQueues(patient(p)));}
  @PostMapping("/api/v1/admin/visits/check-in") @PreAuthorize("hasAuthority('hospital:visit:checkin')") public ApiResponse<OutpatientVisitService.QueueView> staffCheckin(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody CheckinRequest r){if(r.paymentOverride()&&(u.permissions()==null||!u.permissions().contains("hospital:visit:payment-override")))throw new BusinessException(ErrorCode.FORBIDDEN);return ok(visits.checkInStaff(u.id(),r.appointmentId(),r.paymentOverride(),r.reason()));}
  @GetMapping("/api/v1/admin/visits/queues") @PreAuthorize("hasAuthority('hospital:visit:queue:list')") public ApiResponse<List<Map<String,Object>>> queues(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam long scheduleId){return ok(visits.staffQueues(u.id(),scheduleId));}
  @PostMapping("/api/v1/admin/visits/queues/{scheduleId}/call-next") @PreAuthorize("hasAuthority('hospital:visit:queue:call')") public ApiResponse<OutpatientVisitService.QueueView> call(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long scheduleId){return ok(visits.callNext(u.id(),scheduleId));}
  @PostMapping("/api/v1/admin/visits/{encounterId}/recall") @PreAuthorize("hasAuthority('hospital:visit:queue:call')") public ApiResponse<OutpatientVisitService.QueueView> recall(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(visits.recall(u.id(),encounterId));}
  @PostMapping("/api/v1/admin/visits/{encounterId}/skip") @PreAuthorize("hasAuthority('hospital:visit:queue:call')") public ApiResponse<OutpatientVisitService.QueueView> skip(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody Reason r){return ok(visits.skip(u.id(),encounterId,r.reason()));}
  @PostMapping("/api/v1/admin/visits/{encounterId}/return") @PreAuthorize("hasAuthority('hospital:visit:queue:call')") public ApiResponse<OutpatientVisitService.QueueView> back(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody Reason r){return ok(visits.returnToQueue(u.id(),encounterId,r.reason()));}
  @GetMapping("/api/v1/public/queues/{scheduleId}") public ApiResponse<List<Map<String,Object>>> display(@PathVariable long scheduleId){return ok(visits.display(scheduleId));}
  @GetMapping("/api/v1/doctor/visits/workbench") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<List<Map<String,Object>>> workbench(@AuthenticationPrincipal AuthenticatedUser u){return ok(visits.doctorWorkbench(u.id()));}
  @PostMapping("/api/v1/doctor/visits/queues/{scheduleId}/call-next") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<OutpatientVisitService.QueueView> doctorCall(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long scheduleId){return ok(visits.doctorCallNext(u.id(),scheduleId));}
  @GetMapping("/api/v1/doctor/visits/{encounterId}") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(visits.doctorDetail(u.id(),encounterId));}
  @PostMapping("/api/v1/doctor/visits/{encounterId}/start") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<OutpatientVisitService.QueueView> start(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(visits.start(u.id(),encounterId));}
  @PostMapping("/api/v1/doctor/visits/{encounterId}/complete") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<OutpatientVisitService.QueueView> complete(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(visits.complete(u.id(),encounterId));}
  private long patient(AuthenticatedPatient p){if(p==null)throw new BusinessException(ErrorCode.FORBIDDEN);return p.patientId();} private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.get());}
  public record CheckinRequest(long appointmentId,boolean paymentOverride,String reason){} public record Reason(String reason){}
}
