package com.toppaload.ai;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkloadBalanceSuggestionDto(
        UUID overloadedUserId,
        String overloadedUserName,
        BigDecimal overloadedUserUtilPct,
        UUID candidateUserId,
        String candidateUserName,
        BigDecimal candidateUserUtilPct,
        String suggestionText,
        String sharedSkill
) {}

