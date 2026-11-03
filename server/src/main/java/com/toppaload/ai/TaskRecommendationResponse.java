package com.toppaload.ai;

import java.util.List;
import java.util.UUID;

public record TaskRecommendationResponse(
        UUID taskId,
        String taskName,
        boolean aiEnabled,
        List<AssigneeRecommendationDto> recommendations
) {}

