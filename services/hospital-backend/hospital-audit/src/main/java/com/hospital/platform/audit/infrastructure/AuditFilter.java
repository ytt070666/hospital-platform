package com.hospital.platform.audit.infrastructure;

import com.hospital.platform.common.security.AuthenticatedUser;
import com.hospital.platform.common.trace.TraceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuditFilter extends OncePerRequestFilter {
  private final JdbcTemplate jdbc;
  public AuditFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/");
  }

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    try { chain.doFilter(request, response); }
    finally {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
        String result = response.getStatus() < 400 ? "SUCCESS" : "FAIL";
        Resource resource = resource(request.getRequestURI());
        jdbc.update("insert into sys_audit_log(user_id,account_name,action,resource_type,resource_id,result,ip_address,request_path,trace_id) values(?,?,?,?,?,?,?,?,?)", user.id(), user.username(), request.getMethod(), resource.type(), resource.id(), result, ip(request), request.getRequestURI(), TraceId.get());
        if (!"GET".equals(request.getMethod())) jdbc.update("insert into sys_operation_log(user_id,action,resource_type,resource_id,result,ip_address,trace_id) values(?,?,?,?,?,?,?)", user.id(), request.getMethod(), resource.type(), resource.id(), result, ip(request), TraceId.get());
      }
    }
  }

  private Resource resource(String path) {
    String[] segments = path.split("/");
    if (segments.length >= 6 && "master".equals(segments[4])) {
      String id = segments.length >= 7 && segments[6].matches("\\d+") ? segments[6] : null;
      return new Resource("MASTER_" + segments[5].toUpperCase(), id);
    }
    if (segments.length >= 6 && "schedules".equals(segments[4])) {
      return new Resource("SCHEDULE", segments[5].matches("\\d+") ? segments[5] : null);
    }
    if (segments.length >= 6 && "patients".equals(segments[4])) {
      return new Resource("PATIENT", segments[5].matches("\\d+") ? segments[5] : null);
    }
    if (segments.length >= 6 && "appointments".equals(segments[4])) {
      return new Resource("APPOINTMENT", segments[5].matches("\\d+") ? segments[5] : null);
    }
    if (segments.length >= 6 && "visits".equals(segments[4])) {
      return new Resource("VISIT", segments[5].matches("\\d+") ? segments[5] : null);
    }
    if (segments.length >= 6 && "clinic-rooms".equals(segments[4])) {
      return new Resource("CLINIC_ROOM", segments[5].matches("\\d+") ? segments[5] : null);
    }
    if (segments.length >= 6 && "schedule".equals(segments[4])) {
      String resource = segments[5].toUpperCase();
      String id = segments.length >= 7 && segments[6].matches("\\d+") ? segments[6] : null;
      return new Resource("SCHEDULE_" + resource, id);
    }
    return new Resource("API", null);
  }
  private String ip(HttpServletRequest request) { String forwarded = request.getHeader("X-Forwarded-For"); return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim(); }
  private record Resource(String type, String id) { }
}
