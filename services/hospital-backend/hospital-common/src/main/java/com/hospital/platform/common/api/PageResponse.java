package com.hospital.platform.common.api;

import java.util.List;
public record PageResponse<T>(long page, long pageSize, long total, List<T> records) { }
