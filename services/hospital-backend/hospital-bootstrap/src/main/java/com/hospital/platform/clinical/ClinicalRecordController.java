package com.hospital.platform.clinical;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Explicit DTOs only: clinical entities, encrypted identity values and signature internals never cross this boundary. */
@RestController @RequestMapping("/api/v1/doctor/visits/{encounterId}/clinical") public class ClinicalRecordController {
  private final ClinicalService clinical; public ClinicalRecordController(ClinicalService clinical){this.clinical=clinical;}
  @GetMapping @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench') or hasAuthority('hospital:clinical:record:audit')") public ApiResponse<ClinicalService.EncounterClinicalView> detail(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(clinical.detail(user(u),auditor(u),encounterId));}
  @PostMapping("/record/draft") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.RecordView> draft(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId){return ok(clinical.draft(user(u),encounterId));}
  @PutMapping("/record/draft") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.RecordView> save(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody ClinicalService.SaveDraft request){return ok(clinical.saveDraft(user(u),encounterId,request));}
  @PostMapping("/record/sign") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.RecordView> sign(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody ClinicalService.SignRequest request){return ok(clinical.sign(user(u),encounterId,request));}
  @PostMapping("/record/amendments") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.RecordView> amend(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody ClinicalService.AmendRequest request){if(u.permissions()==null||!u.permissions().contains("hospital:clinical:record:amend"))throw new BusinessException(ErrorCode.FORBIDDEN);return ok(clinical.amend(user(u),encounterId,request));}
  @GetMapping("/record/revisions/{revisionId}/integrity") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench') or hasAuthority('hospital:clinical:record:audit')") public ApiResponse<Integrity> integrity(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@PathVariable long revisionId){return ok(new Integrity(clinical.verifyIntegrity(user(u),auditor(u),encounterId,revisionId)));}
  @GetMapping("/diagnosis-catalog") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<List<ClinicalService.DiagnosisCatalogItem>> catalog(@RequestParam(defaultValue="") String query){return ok(clinical.catalogue(query));}
  @PostMapping("/vitals") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.VitalView> vital(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody ClinicalService.VitalInput request){return ok(clinical.vital(user(u),encounterId,request));}
  @PostMapping("/allergies") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<ClinicalService.AllergyView> allergy(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody ClinicalService.AllergyInput request){return ok(clinical.allergy(user(u),encounterId,request));}
  @PutMapping("/allergy-status") @PreAuthorize("hasAuthority('hospital:visit:doctor:workbench')") public ApiResponse<Void> allergyStatus(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long encounterId,@RequestBody AllergyStatus request){clinical.setAllergyStatus(user(u),encounterId,request.status());return ok(null);}
  private static long user(AuthenticatedUser u){if(u==null)throw new BusinessException(ErrorCode.FORBIDDEN);return u.id();}private static boolean auditor(AuthenticatedUser u){return u!=null&&u.permissions()!=null&&u.permissions().contains("hospital:clinical:record:audit");}private static <T>ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.get());}
  public record AllergyStatus(String status){} public record Integrity(boolean valid){}
}
