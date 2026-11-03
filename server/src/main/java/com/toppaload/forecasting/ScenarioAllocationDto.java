package com.toppaload.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ScenarioAllocationDto(
        UUID id,
        UUID scenarioId,
        UUID userId,
        String userName,
        UUID projectId,
        String projectName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal hoursPerDay,
        String notes
) {}

