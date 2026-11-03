package com.toppaload.allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AllocationCreatedEvent(
        UUID allocationId,
        UUID userId,
        UUID projectId,
        String projectName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal hoursPerDay,
        AllocationType allocationType
) {}

