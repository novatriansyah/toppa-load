package com.toppaload.forecasting;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AddScenarioAllocationRequest(
        @NotNull UUID userId,
        UUID projectId,
        @NotBlank String projectName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.1") @DecimalMax("24.0") BigDecimal hoursPerDay,
        String notes
) {}

