package com.hospital.platform.diagnostic;
import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
/** Patient DTO intentionally excludes critical rules, staff notes, audit and provider metadata. */
@RestController @RequestMapping("/api/v1/patient/diagnostic-reports") public class PatientDiagnosticController {
 private final DiagnosticService service; public PatientDiagnosticController(DiagnosticService service){this.service=service;}
 @GetMapping public ApiResponse<List<DiagnosticService.ReportView>> list(@AuthenticationPrincipal AuthenticatedPatient p){return ok(service.patientReports(patient(p)));}
 @GetMapping("/{id}") public ApiResponse<DiagnosticService.PatientReport> detail(@AuthenticationPrincipal AuthenticatedPatient p,@PathVariable long id){return ok(service.patientReport(patient(p),id));}
 private static long patient(AuthenticatedPatient p){if(p==null)throw new BusinessException(ErrorCode.FORBIDDEN);return p.patientId();} private static <T>ApiResponse<T> ok(T v){return ApiResponse.success(v,TraceId.get());}
}
