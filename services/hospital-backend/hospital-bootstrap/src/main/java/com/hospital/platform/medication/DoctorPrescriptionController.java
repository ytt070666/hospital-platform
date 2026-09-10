package com.hospital.platform.medication;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Explicit DTO API; request doctor IDs and client supplied signer fields do not exist. */
@RestController @RequestMapping("/api/v1/doctor/visits/{encounterId}/prescriptions") public class DoctorPrescriptionController {
  private final MedicationService service; public DoctorPrescriptionController(MedicationService service){this.service=service;}
  @GetMapping @PreAuthorize("hasAuthority('hospital:clinical:prescription:view')") public ApiResponse<List<MedicationService.PrescriptionView>> list(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(service.doctorList(user(u),encounterId));}
  @GetMapping("/drug-catalog") @PreAuthorize("hasAuthority('hospital:clinical:prescription:create')") public ApiResponse<List<MedicationService.DrugView>> catalog(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestParam(defaultValue="") String query){return ok(service.catalog(user(u),encounterId,query));}
  @PostMapping @PreAuthorize("hasAuthority('hospital:clinical:prescription:create')") public ApiResponse<MedicationService.PrescriptionView> create(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody(required=false) CreateInput input){return ok(service.createDraft(user(u),encounterId,input==null?null:input.supersedesPrescriptionId()));}
  @GetMapping("/{prescriptionId}") @PreAuthorize("hasAuthority('hospital:clinical:prescription:view')") public ApiResponse<MedicationService.PrescriptionView> detail(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId){return ok(service.doctorDetail(user(u),encounterId,prescriptionId));}
  @PostMapping("/{prescriptionId}/items") @PreAuthorize("hasAuthority('hospital:clinical:prescription:create')") public ApiResponse<MedicationService.PrescriptionView> add(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId,@RequestBody MedicationService.ItemInput input){return ok(service.addItem(user(u),encounterId,prescriptionId,input));}
  @DeleteMapping("/{prescriptionId}/items/{itemId}") @PreAuthorize("hasAuthority('hospital:clinical:prescription:create')") public ApiResponse<MedicationService.PrescriptionView> remove(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId,@PathVariable long itemId){return ok(service.removeItem(user(u),encounterId,prescriptionId,itemId));}
  @PostMapping("/{prescriptionId}/sign") @PreAuthorize("hasAuthority('hospital:clinical:prescription:sign')") public ApiResponse<MedicationService.PrescriptionView> sign(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId,@RequestBody MedicationService.VersionInput input){return ok(service.sign(user(u),encounterId,prescriptionId,input));}
  @PostMapping("/{prescriptionId}/submit") @PreAuthorize("hasAuthority('hospital:clinical:prescription:sign')") public ApiResponse<MedicationService.PrescriptionView> submit(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId,@RequestBody MedicationService.SubmitInput input){return ok(service.submit(user(u),encounterId,prescriptionId,input,u!=null&&u.permissions()!=null&&u.permissions().contains("hospital:medication:safety:override")));}
  @PostMapping("/{prescriptionId}/cancel") @PreAuthorize("hasAuthority('hospital:clinical:prescription:cancel')") public ApiResponse<MedicationService.PrescriptionView> cancel(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId,@RequestBody MedicationService.CancelInput input){return ok(service.cancel(user(u),encounterId,prescriptionId,input));}
  @GetMapping("/{prescriptionId}/integrity") @PreAuthorize("hasAuthority('hospital:clinical:prescription:view')") public ApiResponse<Integrity> integrity(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long prescriptionId){return ok(new Integrity(service.verifyIntegrity(user(u),encounterId,prescriptionId)));}
  private static long user(AuthenticatedUser u){if(u==null)throw new BusinessException(ErrorCode.FORBIDDEN);return u.id();} private static <T>ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.get());}
  public record CreateInput(Long supersedesPrescriptionId){} public record Integrity(boolean valid){}
}
