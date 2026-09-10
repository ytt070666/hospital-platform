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

@RestController @RequestMapping("/api/v1/pharmacy/prescriptions") public class PharmacyPrescriptionController {
  private final MedicationService service; public PharmacyPrescriptionController(MedicationService service){this.service=service;}
  @GetMapping("/locations") @PreAuthorize("hasAuthority('hospital:pharmacy:review:list')") public ApiResponse<List<MedicationService.PharmacyView>> locations(@AuthenticationPrincipal AuthenticatedUser u){return ok(service.pharmacies(user(u)));}
  @GetMapping("/inventory") @PreAuthorize("hasAuthority('hospital:pharmacy:inventory:view')") public ApiResponse<List<MedicationService.InventoryLotView>> inventory(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam long pharmacyId){return ok(service.inventory(user(u),pharmacyId));}
  @PostMapping("/inventory/adjustments") @PreAuthorize("hasAuthority('hospital:pharmacy:inventory:manage')") public ApiResponse<MedicationService.InventoryLotView> adjustInventory(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody MedicationService.InventoryAdjustmentInput input){return ok(service.adjustInventory(user(u),input));}
  @GetMapping @PreAuthorize("hasAuthority('hospital:pharmacy:review:list')") public ApiResponse<List<MedicationService.PrescriptionView>> queue(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam long pharmacyId,@RequestParam(required=false) String status){return ok(service.pharmacyQueue(user(u),pharmacyId,status));}
  @PostMapping("/{prescriptionId}/review") public ApiResponse<MedicationService.PrescriptionView> review(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long prescriptionId,@RequestBody MedicationService.ReviewInput input){guard(u,input.decision());return ok(service.review(user(u),prescriptionId,input));}
  @PostMapping("/{prescriptionId}/dispense") @PreAuthorize("hasAuthority('hospital:pharmacy:dispense')") public ApiResponse<MedicationService.PrescriptionView> dispense(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long prescriptionId,@RequestBody PharmacyInput input){return ok(service.dispense(user(u),prescriptionId,input.pharmacyId()));}
  @PostMapping("/returns/{returnId}/complete") @PreAuthorize("hasAuthority('hospital:pharmacy:return')") public ApiResponse<MedicationService.ReturnView> completeReturn(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long returnId,@RequestBody ReturnCompleteInput input){return ok(service.completeReturn(user(u),returnId,input.restockEligible()));}
  private static void guard(AuthenticatedUser u,String decision){if(u==null||u.permissions()==null)throw new BusinessException(ErrorCode.FORBIDDEN);String p="APPROVED".equals(decision)?"hospital:pharmacy:review:approve":"REJECTED".equals(decision)?"hospital:pharmacy:review:reject":null;if(p==null||!u.permissions().contains(p))throw new BusinessException(ErrorCode.FORBIDDEN);}private static long user(AuthenticatedUser u){if(u==null)throw new BusinessException(ErrorCode.FORBIDDEN);return u.id();}private static <T>ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.get());}
  public record PharmacyInput(long pharmacyId){} public record ReturnCompleteInput(boolean restockEligible){}
}
