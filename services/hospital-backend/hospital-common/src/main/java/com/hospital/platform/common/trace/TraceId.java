package com.hospital.platform.common.trace;
import java.util.Optional; import org.slf4j.MDC;
public final class TraceId { public static final String KEY="traceId"; private TraceId(){} public static String get(){return Optional.ofNullable(MDC.get(KEY)).orElse("-");} }
