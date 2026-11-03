package com.toppaload.project;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SavedViewDto(
        UUID id,
        UUID userId,
        String viewType,
        String name,
        Map<String, Object> filterStateJson,
        boolean isShared,
        Instant createdAt
) {}

