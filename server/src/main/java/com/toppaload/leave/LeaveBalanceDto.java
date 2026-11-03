package com.toppaload.leave;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceDto(
        UUID id,
        UUID userId,
        String userFullName,
        int year,
        BigDecimal totalAllowanceDays,
        BigDecimal usedDays,
        BigDecimal remainingDays
) {}

