package com.hospital.platform.visit;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/clinic-rooms") public class ClinicRoomController {
  private final ClinicRoomService rooms; public ClinicRoomController(ClinicRoomService rooms){this.rooms=rooms;}
  @GetMapping @PreAuthorize("hasAuthority('hospital:clinic-room:list')") public ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(required=false)Long outpatientDepartmentId){return ok(rooms.list(u.id(),outpatientDepartmentId));}
  @PostMapping @PreAuthorize("hasAuthority('hospital:clinic-room:manage')") public ApiResponse<Map<String,String>> create(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody ClinicRoomService.RoomRequest r){return ok(Map.of("id",Long.toString(rooms.create(u.id(),r))));}
  @PutMapping("/{id}") @PreAuthorize("hasAuthority('hospital:clinic-room:manage')") public ApiResponse<Void> update(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestBody ClinicRoomService.RoomRequest r){rooms.update(u.id(),id,r);return ok(null);}
  @PostMapping("/{roomId}/assign-schedule/{scheduleId}") @PreAuthorize("hasAuthority('hospital:clinic-room:manage')") public ApiResponse<Void> assign(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long roomId,@PathVariable long scheduleId,@RequestBody Version r){rooms.assignSchedule(u.id(),scheduleId,roomId,r.version());return ok(null);}
  private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.get());} public record Version(int version){}
}
