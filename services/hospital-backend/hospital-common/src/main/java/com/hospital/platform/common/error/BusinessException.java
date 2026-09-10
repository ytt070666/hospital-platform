package com.hospital.platform.common.error;
public class BusinessException extends RuntimeException { private final ErrorCode errorCode; public BusinessException(ErrorCode e) { super(e.message()); this.errorCode=e; } public ErrorCode errorCode(){ return errorCode; } }
