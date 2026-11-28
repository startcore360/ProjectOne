package com.edu360.api.student;

import java.util.List;
import java.util.Map;

public record StudentDirectoryResponse(List<Map<String, Object>> items, String nextCursor, boolean hasMore) {}