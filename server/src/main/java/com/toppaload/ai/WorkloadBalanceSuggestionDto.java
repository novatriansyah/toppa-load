package com.toppaload.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkloadBalanceSuggestionDto(
        UUID overloadedUserId,
        String overloadedUserName,
        BigDecimal overloadedUserUtilPct,
        UUID candidateUserId,
        String candidateUserName,
        BigDecimal candidateUserUtilPct,
        BigDecimal candidateAvailableHours,
        UUID allocationId,
        UUID projectId,
        String projectCode,
        String projectName,
        UUID taskId,
        String taskTitle,
        BigDecimal hoursPerDay,
        LocalDate startDate,
        LocalDate endDate,
        String suggestionText,
        String sharedSkill
) {}
