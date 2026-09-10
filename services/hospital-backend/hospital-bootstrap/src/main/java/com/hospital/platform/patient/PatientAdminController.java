package com.hospital.platform.patient;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.api.PageResponse;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/patients")
public class PatientAdminController {
  private final PatientIdentityService patients;
  public PatientAdminController(PatientIdentityService patients) { this.patients=patients; }
  @GetMapping @PreAuthorize("hasAuthority('hospital:patient:list')") public ApiResponse<PageResponse<PatientIdentityService.AdminPatientDto>> list(@RequestParam(required=false) String patientNo,@RequestParam(required=false) String mobile,@RequestParam(required=false) String idNumber,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.success(patients.adminPatients(patientNo,mobile,idNumber,Math.max(1,page),Math.min(100,Math.max(1,pageSize))),TraceId.get());}
  @GetMapping("/{patientId}") @PreAuthorize("hasAuthority('hospital:patient:view')") public ApiResponse<PatientIdentityService.AdminPatientDto> detail(@PathVariable long patientId){return ApiResponse.success(patients.adminPatient(patientId),TraceId.get());}
  @GetMapping("/{patientId}/members") @PreAuthorize("hasAuthority('hospital:patient:member:list')") public ApiResponse<List<PatientIdentityService.MemberDto>> members(@PathVariable long patientId){patients.adminPatient(patientId);return ApiResponse.success(patients.members(patientId),TraceId.get());}
  @PostMapping("/{patientId}/disable") @PreAuthorize("hasAuthority('hospital:patient:disable')") public ApiResponse<Void> disable(@PathVariable long patientId){patients.disablePatient(patientId);return ApiResponse.success(null,TraceId.get());}
}
