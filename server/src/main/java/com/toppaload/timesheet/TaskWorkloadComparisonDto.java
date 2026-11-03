package com.toppaload.timesheet;

import java.math.BigDecimal;
import java.util.UUID;

public record TaskWorkloadComparisonDto(
        UUID taskId,
        String taskTitle,
        UUID projectId,
        String projectCode,
        BigDecimal estimatedHours,
        BigDecimal allocatedHours,
        BigDecimal actualLoggedHours
) {}

