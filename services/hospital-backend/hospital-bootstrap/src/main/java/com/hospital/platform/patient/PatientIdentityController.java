package com.hospital.platform.patient;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedPatient;
import com.hospital.platform.common.trace.TraceId;
import com.hospital.platform.iam.application.PatientTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient")
public class PatientIdentityController {
  private final PatientIdentityService patients;
  public PatientIdentityController(PatientIdentityService patients) { this.patients=patients; }
  @PostMapping("/auth/send-code") public ApiResponse<PatientIdentityService.SendCodeResult> sendCode(@Valid @RequestBody SendCodeRequest request,HttpServletRequest servlet){return ApiResponse.success(patients.sendCode(request.mobile(),ip(servlet)),TraceId.get());}
  @PostMapping("/auth/login") public ApiResponse<PatientTokenService.TokenPair> login(@Valid @RequestBody LoginRequest request,HttpServletRequest servlet){return ApiResponse.success(patients.login(request.mobile(),request.code(),request.clientType(),ip(servlet),servlet.getHeader("User-Agent")),TraceId.get());}
  @PostMapping("/auth/refresh") public ApiResponse<PatientTokenService.TokenPair> refresh(@Valid @RequestBody RefreshRequest request,HttpServletRequest servlet){return ApiResponse.success(patients.refresh(request.refreshToken(),request.clientType(),ip(servlet),servlet.getHeader("User-Agent")),TraceId.get());}
  @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody LogoutRequest request){patients.logout(request.refreshToken());}
  @GetMapping("/me") public ApiResponse<PatientIdentityService.PatientSelfDto> me(@AuthenticationPrincipal AuthenticatedPatient patient){return ApiResponse.success(patients.me(id(patient)),TraceId.get());}
  @PutMapping("/me") public ApiResponse<Void> update(@AuthenticationPrincipal AuthenticatedPatient patient,@Valid @RequestBody ProfileRequest request){patients.updateProfile(id(patient),new PatientIdentityService.ProfileRequest(request.name(),request.gender(),request.birthDate()));return ApiResponse.success(null,TraceId.get());}
  @GetMapping("/members") public ApiResponse<List<PatientIdentityService.MemberDto>> members(@AuthenticationPrincipal AuthenticatedPatient patient){return ApiResponse.success(patients.members(id(patient)),TraceId.get());}
  @PostMapping("/members") public ApiResponse<Long> createMember(@AuthenticationPrincipal AuthenticatedPatient patient,@Valid @RequestBody MemberRequest request){return ApiResponse.success(patients.createMember(id(patient),request.toService()),TraceId.get());}
  @GetMapping("/members/{memberId}") public ApiResponse<PatientIdentityService.MemberDto> member(@AuthenticationPrincipal AuthenticatedPatient patient,@PathVariable long memberId){return ApiResponse.success(patients.member(id(patient),memberId),TraceId.get());}
  @PutMapping("/members/{memberId}") public ApiResponse<Void> updateMember(@AuthenticationPrincipal AuthenticatedPatient patient,@PathVariable long memberId,@Valid @RequestBody MemberRequest request){patients.updateMember(id(patient),memberId,request.toService());return ApiResponse.success(null,TraceId.get());}
  @PostMapping("/members/{memberId}/disable") public ApiResponse<Void> disableMember(@AuthenticationPrincipal AuthenticatedPatient patient,@PathVariable long memberId){patients.disableMember(id(patient),memberId);return ApiResponse.success(null,TraceId.get());}
  @PostMapping("/members/{memberId}/default") public ApiResponse<Void> defaultMember(@AuthenticationPrincipal AuthenticatedPatient patient,@PathVariable long memberId){patients.defaultMember(id(patient),memberId);return ApiResponse.success(null,TraceId.get());}
  private long id(AuthenticatedPatient patient){if(patient==null)throw new BusinessException(ErrorCode.FORBIDDEN);return patient.patientId();}private String ip(HttpServletRequest request){String forwarded=request.getHeader("X-Forwarded-For");return forwarded==null?request.getRemoteAddr():forwarded.split(",")[0].trim();}
  public record SendCodeRequest(@NotBlank @Size(max=32) String mobile){} public record LoginRequest(@NotBlank String mobile,@NotBlank @Pattern(regexp="\\d{6}") String code,@NotBlank @Size(max=32) String clientType){} public record RefreshRequest(@NotBlank String refreshToken,@NotBlank @Size(max=32) String clientType){} public record LogoutRequest(@NotBlank String refreshToken){} public record ProfileRequest(@NotBlank @Size(max=64) String name,@Size(max=16) String gender,LocalDate birthDate){} public record MemberRequest(@NotBlank @Pattern(regexp="SELF|CHILD|PARENT|SPOUSE|OTHER") String relationship,@NotBlank @Size(max=64) String name,@Size(max=16) String gender,LocalDate birthDate,@Size(max=32) String idType,@Size(max=64) String idNumber,@Size(max=32) String mobile,Long guardianMemberId){PatientIdentityService.MemberRequest toService(){return new PatientIdentityService.MemberRequest(relationship,name,gender,birthDate,idType,idNumber,mobile,guardianMemberId);}}
}
