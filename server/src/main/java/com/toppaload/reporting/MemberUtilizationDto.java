package com.toppaload.reporting;

import java.math.BigDecimal;
import java.util.UUID;

public record MemberUtilizationDto(
        UUID userId,
        String name,
        String email,
        String role,
        BigDecimal weeklyCapacityHours,
        BigDecimal netCapacityHours,
        BigDecimal allocatedHours,
        BigDecimal timesheetHours,
        BigDecimal utilizationPct,
        int overloadWeekCount,
        String status
) {}

