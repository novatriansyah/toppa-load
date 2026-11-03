package com.toppaload.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UtilizationSummaryDto(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal overallUtilizationPct,
        BigDecimal totalCapacityHours,
        BigDecimal totalAllocatedHours,
        BigDecimal totalActualHours,
        int overloadedMembersCount,
        List<MemberUtilizationDto> members
) {}

