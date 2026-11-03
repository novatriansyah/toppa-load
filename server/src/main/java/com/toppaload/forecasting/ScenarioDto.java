package com.toppaload.forecasting;

import java.time.Instant;
import java.util.UUID;

public record ScenarioDto(
        UUID id,
        String name,
        String description,
        UUID createdBy,
        String createdByName,
        String status,
        int allocationsCount,
        Instant createdAt,
        Instant updatedAt
) {}

