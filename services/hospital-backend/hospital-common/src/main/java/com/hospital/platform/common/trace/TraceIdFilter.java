package com.hospital.platform.common.trace;
import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException; import java.util.UUID; import org.slf4j.MDC; import org.springframework.core.Ordered; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter { protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException { String id=req.getHeader("X-Trace-Id"); if(id==null||!id.matches("[A-Za-z0-9-]{8,64}")) id=UUID.randomUUID().toString(); MDC.put(TraceId.KEY,id); res.setHeader("X-Trace-Id",id); try{chain.doFilter(req,res);}finally{MDC.remove(TraceId.KEY);} } }
