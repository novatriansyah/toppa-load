package com.toppaload.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AssigneeRecommendationDto(
        UUID userId,
        String fullName,
        String email,
        String role,
        int matchScore,
        BigDecimal availableHours,
        BigDecimal currentUtilizationPct,
        BigDecimal projectedUtilizationPct,
        boolean willOverload,
        List<String> matchingSkills,
        String reason
) {}

