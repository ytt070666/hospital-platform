package com.hospital.platform.appointment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.api.PageResponse;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Administrative appointment endpoints expose a purpose-built, masked DTO and always resolve data scope in SQL. */
@RestController @RequestMapping("/api/v1/admin/appointments") public class AdminAppointmentController {
  private final AppointmentService appointments; public AdminAppointmentController(AppointmentService appointments){this.appointments=appointments;}
  @GetMapping @PreAuthorize("hasAuthority('hospital:appointment:list')") public ApiResponse<PageResponse<AppointmentService.AdminListView>> list(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false)String appointmentNo,@RequestParam(required=false)String patientNo,@RequestParam(required=false)String memberName,@RequestParam(required=false)Long doctorId,@RequestParam(required=false)Long departmentId,@RequestParam(required=false)Long campusId,@RequestParam(required=false)Long clinicTypeId,@RequestParam(required=false)LocalDate scheduleDate,@RequestParam(required=false)String status,@RequestParam(required=false)LocalDate createdFrom,@RequestParam(required=false)LocalDate createdTo,@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="20")long pageSize){return ok(appointments.adminList(user.id(),new AppointmentService.AdminQuery(appointmentNo,patientNo,memberName,doctorId,departmentId,campusId,clinicTypeId,scheduleDate,status,createdFrom,createdTo,page,pageSize)));}
  @GetMapping("/{id}") @PreAuthorize("hasAuthority('hospital:appointment:view')") public ApiResponse<AppointmentService.AdminDetailView> detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id){return ok(appointments.adminDetail(user.id(),id));}
  @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('hospital:appointment:cancel')") public ApiResponse<AppointmentService.View> cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody CancelRequest request){return ok(appointments.cancelByStaff(user.id(),id,request.reasonCode(),request.reason()));}
  private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.get());} public record CancelRequest(String reasonCode,String reason){}
}
