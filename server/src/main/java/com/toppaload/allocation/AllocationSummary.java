package com.toppaload.allocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AllocationSummary(
    UUID id,
    UUID userId,
    String userName,
    UUID projectId,
    String projectCode,
    String projectName,
    UUID taskId,
    String taskTitle,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal hoursPerDay,
    AllocationType allocationType,
    String notes,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}

