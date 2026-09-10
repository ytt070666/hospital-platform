package com.hospital.platform.scheduling;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.api.PageResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchedulingController {
  private final SchedulingService service; public SchedulingController(SchedulingService service){this.service=service;}
  @GetMapping("/api/v1/admin/schedule/sessions") @PreAuthorize("hasAuthority('hospital:schedule-session:manage')") public ApiResponse<List<Map<String,Object>>> sessions(@RequestParam(required=false) Long hospitalId){return ok(service.sessions(hospitalId,false));}
  @PostMapping("/api/v1/admin/schedule/sessions") @PreAuthorize("hasAuthority('hospital:schedule-session:manage')") public ApiResponse<Map<String,String>> createSession(@RequestBody SchedulingService.SessionRequest r){return ok(Map.of("id",Long.toString(service.createSession(r))));}
  @PutMapping("/api/v1/admin/schedule/sessions/{id}") @PreAuthorize("hasAuthority('hospital:schedule-session:manage')") public ApiResponse<Void> updateSession(@PathVariable long id,@RequestBody SchedulingService.SessionRequest r){service.updateSession(id,r);return ok(null);}
  @GetMapping("/api/v1/admin/schedule/templates") @PreAuthorize("hasAuthority('hospital:schedule-template:list')") public ApiResponse<List<Map<String,Object>>> templates(@AuthenticationPrincipal AuthenticatedUser user){return ok(service.templates(user.id()));}
  @PostMapping("/api/v1/admin/schedule/templates") @PreAuthorize("hasAuthority('hospital:schedule-template:manage')") public ApiResponse<Map<String,String>> createTemplate(@RequestBody SchedulingService.TemplateRequest r,@AuthenticationPrincipal AuthenticatedUser user){return ok(Map.of("id",Long.toString(service.createTemplate(r,user.id()))));}
  @PutMapping("/api/v1/admin/schedule/templates/{id}") @PreAuthorize("hasAuthority('hospital:schedule-template:manage')") public ApiResponse<Void> updateTemplate(@PathVariable long id,@RequestBody SchedulingService.TemplateRequest r,@AuthenticationPrincipal AuthenticatedUser user){service.updateTemplate(id,r,user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedule/templates/{id}/preview") @PreAuthorize("hasAuthority('hospital:schedule-template:list')") public ApiResponse<List<Map<String,Object>>> preview(@PathVariable long id,@RequestBody DateRange r){return ok(service.previewTemplate(id,r.from(),r.to()));}
  @PostMapping("/api/v1/admin/schedule/templates/{id}/generate") @PreAuthorize("hasAuthority('hospital:schedule-template:manage')") public ApiResponse<List<Long>> generate(@PathVariable long id,@RequestBody DateRange r,@AuthenticationPrincipal AuthenticatedUser user){return ok(service.generateTemplate(id,r.from(),r.to(),user.id()));}
  @GetMapping("/api/v1/admin/schedules") @PreAuthorize("hasAuthority('hospital:schedule:list')") public ApiResponse<PageResponse<Map<String,Object>>> schedules(@AuthenticationPrincipal AuthenticatedUser user,ScheduleQuery q){return ok(service.schedules(user.id(),q.filter(),false));}
  @PostMapping("/api/v1/admin/schedules") @PreAuthorize("hasAuthority('hospital:schedule:create')") public ApiResponse<Map<String,String>> createSchedule(@RequestBody SchedulingService.ScheduleRequest r,@AuthenticationPrincipal AuthenticatedUser user){return ok(Map.of("id",Long.toString(service.createSchedule(r,user.id()))));}
  @PostMapping("/api/v1/admin/schedules/{id}/publish") @PreAuthorize("hasAuthority('hospital:schedule:publish')") public ApiResponse<Void> publish(@PathVariable long id,@RequestBody Version r,@AuthenticationPrincipal AuthenticatedUser user){service.publish(id,r.version(),user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedules/{id}/unpublish") @PreAuthorize("hasAuthority('hospital:schedule:publish')") public ApiResponse<Void> unpublish(@PathVariable long id,@RequestBody Version r,@AuthenticationPrincipal AuthenticatedUser user){service.unpublish(id,r.version(),user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedules/{id}/stop") @PreAuthorize("hasAuthority('hospital:schedule:stop')") public ApiResponse<Void> stop(@PathVariable long id,@RequestBody Stop r,@AuthenticationPrincipal AuthenticatedUser user){service.stop(id,r.reasonCode(),r.reason(),r.version(),user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedules/{id}/substitute") @PreAuthorize("hasAuthority('hospital:schedule:substitute')") public ApiResponse<Void> substitute(@PathVariable long id,@RequestBody Substitute r,@AuthenticationPrincipal AuthenticatedUser user){service.substitute(id,r.doctorId(),r.reason(),r.version(),user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedules/{id}/quota") @PreAuthorize("hasAuthority('hospital:schedule:quota')") public ApiResponse<Void> quota(@PathVariable long id,@RequestBody Quota r,@AuthenticationPrincipal AuthenticatedUser user){service.adjustQuota(id,r.totalQuota(),r.reason(),r.version(),user.id());return ok(null);}
  @PostMapping("/api/v1/admin/schedules/{id}/slots/generate") @PreAuthorize("hasAuthority('hospital:schedule:update')") public ApiResponse<List<Map<String,Object>>> slots(@PathVariable long id,@RequestBody SlotGenerate r,@AuthenticationPrincipal AuthenticatedUser user){return ok(service.generateSlots(id,r.durationMinutes(),r.quota(),user.id()));}
  @PutMapping("/api/v1/admin/schedules/{id}/slots") @PreAuthorize("hasAuthority('hospital:schedule:update')") public ApiResponse<Void> replaceSlots(@PathVariable long id,@RequestBody List<Map<String,Object>> slots,@AuthenticationPrincipal AuthenticatedUser user){service.replaceSlots(id,slots,user.id());return ok(null);}
  @GetMapping("/api/v1/admin/schedules/{id}/changes") @PreAuthorize("hasAuthority('hospital:schedule:list')") public ApiResponse<List<Map<String,Object>>> changes(@PathVariable long id){return ok(service.changes(id));}
  @GetMapping("/api/v1/public/schedules") public ApiResponse<PageResponse<Map<String,Object>>> publicSchedules(ScheduleQuery q){return ok(publicView(service.schedules(0,q.filter(),true)));}
  @GetMapping("/api/v1/public/doctors/{doctorId}/schedules") public ApiResponse<PageResponse<Map<String,Object>>> publicDoctorSchedules(@PathVariable long doctorId,ScheduleQuery q){return ok(publicView(service.schedules(0,new SchedulingService.ScheduleFilter(doctorId,q.departmentId(),q.campusId(),q.clinicTypeId(),q.from(),q.to(),q.page(),q.pageSize()),true)));}
  @GetMapping("/api/v1/public/departments/{departmentId}/schedules") public ApiResponse<PageResponse<Map<String,Object>>> publicDepartmentSchedules(@PathVariable long departmentId,ScheduleQuery q){return ok(publicView(service.schedules(0,new SchedulingService.ScheduleFilter(q.doctorId(),departmentId,q.campusId(),q.clinicTypeId(),q.from(),q.to(),q.page(),q.pageSize()),true)));}
  private PageResponse<Map<String,Object>> publicView(PageResponse<Map<String,Object>> page){return new PageResponse<>(page.page(),page.pageSize(),page.total(),page.records().stream().map(this::safe).toList());}
  private Map<String,Object> safe(Map<String,Object> source){Map<String,Object> view=new LinkedHashMap<>();for(String k:List.of("id","doctorName","originalDoctorName","departmentId","departmentName","outpatientDepartmentName","campusName","clinicType","sessionName","scheduleDate","startTime","endTime","totalQuota","availableQuota","slotMode"))view.put(k,source.get(k));view.put("slots",((List<Map<String,Object>>)source.get("slots")).stream().map(slot->{Map<String,Object> item=new LinkedHashMap<>();for(String k:List.of("id","startTime","endTime","totalQuota","availableQuota","status"))item.put(k,slot.get(k));return item;}).toList());view.put("substituted",source.get("substituteDoctorId")!=null);view.put("statusForPatient",((Number)source.get("availableQuota")).intValue()>0?"AVAILABLE":"FULL");return view;}
  private static <T> ApiResponse<T> ok(T value){return ApiResponse.success(value,TraceId.get());}
  public record DateRange(LocalDate from,LocalDate to){} public record Version(int version){} public record Stop(String reasonCode,String reason,int version){} public record Substitute(long doctorId,String reason,int version){} public record Quota(int totalQuota,String reason,int version){} public record SlotGenerate(int durationMinutes,int quota){}
  public record ScheduleQuery(Long doctorId,Long departmentId,Long campusId,Long clinicTypeId,LocalDate from,LocalDate to,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long pageSize){SchedulingService.ScheduleFilter filter(){return new SchedulingService.ScheduleFilter(doctorId,departmentId,campusId,clinicTypeId,from,to,page,pageSize);}}
}
