package com.hospital.platform.payment;

import com.hospital.platform.common.api.ApiResponse;
import com.hospital.platform.common.trace.TraceId;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-10)
@RestControllerAdvice
public class PaymentExceptionHandler {
  @ExceptionHandler(PaymentException.class)
  ResponseEntity<ApiResponse<Void>> handle(PaymentException e) {
    return ResponseEntity.status(e.status()).body(ApiResponse.failure(e.getMessage(), e.getMessage(), TraceId.get()));
  }
}
