package com.hospital.platform.diagnostic;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.error.BusinessException;
import com.hospital.platform.common.error.ErrorCode;
import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/examinations") public class ExaminationController {
  private final ExaminationService service; public ExaminationController(ExaminationService service){this.service=service;}
  @GetMapping("/orders") @PreAuthorize("hasAuthority('hospital:clinical:examination:order:list')") public ApiResponse<List<ExaminationService.Order>> orders(@AuthenticationPrincipal AuthenticatedUser u){return ok(service.orders(user(u)));}
  @PostMapping("/orders/{id}/accept") @PreAuthorize("hasAuthority('hospital:clinical:examination:accept')") public ApiResponse<ExaminationService.Order> accept(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestBody ExaminationService.Version input){return ok(service.accept(user(u),id,input));}
  @PostMapping("/orders/{id}/start") @PreAuthorize("hasAuthority('hospital:clinical:examination:start')") public ApiResponse<ExaminationService.Order> start(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestBody ExaminationService.Version input){return ok(service.start(user(u),id,input));}
  @PostMapping("/orders/{id}/complete") @PreAuthorize("hasAuthority('hospital:clinical:examination:complete')") public ApiResponse<ExaminationService.Order> complete(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestBody ExaminationService.Version input){return ok(service.complete(user(u),id,input));}
  @PostMapping("/orders/{id}/reports") @PreAuthorize("hasAuthority('hospital:clinical:examination:report:create')") public ApiResponse<ExaminationService.Report> report(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestBody ExaminationService.Input input){return ok(service.createReport(user(u),id,input));}
  private static long user(AuthenticatedUser u){if(u==null)throw new BusinessException(ErrorCode.FORBIDDEN);return u.id();} private static <T> ApiResponse<T> ok(T data){return ApiResponse.success(data,TraceId.get());}
}
