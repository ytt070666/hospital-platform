package com.hospital.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(Object code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) { return new ApiResponse<>(0, "success", data, traceId); }
    public static ApiResponse<Void> failure(String code, String message, String traceId) { return new ApiResponse<>(code, message, null, traceId); }
}
