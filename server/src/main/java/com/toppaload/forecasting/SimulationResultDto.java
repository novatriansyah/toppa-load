package com.toppaload.forecasting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SimulationResultDto(
        UUID scenarioId,
        String scenarioName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal baselineOverallUtilPct,
        BigDecimal simulatedOverallUtilPct,
        int baselineOverloadedCount,
        int simulatedOverloadedCount,
        List<UserSimulationDeltaDto> userDeltas
) {}

