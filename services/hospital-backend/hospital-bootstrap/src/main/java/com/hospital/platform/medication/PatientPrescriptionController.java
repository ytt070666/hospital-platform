package com.hospital.platform.medication;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Patient responses intentionally omit safety rule internals, pharmacy notes, inventory and audit data. */
@RestController @RequestMapping("/api/v1/patient/prescriptions") public class PatientPrescriptionController {
  private final MedicationService service; public PatientPrescriptionController(MedicationService service){this.service=service;}
  @GetMapping public ApiResponse<List<PatientPrescriptionView>> list(@AuthenticationPrincipal AuthenticatedPatient p){return ok(service.patientList(patient(p)).stream().map(PatientPrescriptionView::from).toList());}
  @GetMapping("/{prescriptionId}") public ApiResponse<PatientPrescriptionView> detail(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long prescriptionId){return ok(PatientPrescriptionView.from(service.patientDetail(patient(p),prescriptionId)));}
  @PostMapping("/{prescriptionId}/returns") public ApiResponse<MedicationService.ReturnView> requestReturn(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long prescriptionId,@RequestBody ReturnRequest input){return ok(service.requestReturn(patient(p),prescriptionId,input.items(),input.reasonCode(),input.reason()));}
  private static long patient(AuthenticatedPatient p){if(p==null)throw new BusinessException(ErrorCode.FORBIDDEN);return p.patientId();}private static <T>ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.get());}
  public record ReturnRequest(List<MedicationService.ReturnItemInput> items,String reasonCode,String reason){} public record PatientItem(String drugName,String strength,String dosageForm,java.math.BigDecimal doseAmount,String doseUnit,String routeName,String frequencyName,int durationValue,String durationUnit,java.math.BigDecimal quantity,String quantityUnit){} public record PatientPrescriptionView(long id,String prescriptionNo,String status,List<PatientItem> items){static PatientPrescriptionView from(MedicationService.PrescriptionView p){return new PatientPrescriptionView(p.id(),p.prescriptionNo(),p.status(),p.items().stream().map(i->new PatientItem(i.drugName(),i.strength(),i.dosageForm(),i.doseAmount(),i.doseUnit(),i.routeName(),i.frequencyName(),i.durationValue(),i.durationUnit(),i.quantity(),i.quantityUnit())).toList());}}
}
