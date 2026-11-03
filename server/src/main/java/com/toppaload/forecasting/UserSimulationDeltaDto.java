package com.toppaload.forecasting;

import java.math.BigDecimal;
import java.util.UUID;

public record UserSimulationDeltaDto(
        UUID userId,
        String userName,
        BigDecimal capacityHours,
        BigDecimal baselineAllocatedHours,
        BigDecimal simulatedDeltaHours,
        BigDecimal totalSimulatedHours,
        BigDecimal baselineUtilPct,
        BigDecimal simulatedUtilPct,
        String baselineStatus,
        String simulatedStatus,
        boolean isNewlyOverloaded
) {}

