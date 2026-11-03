package com.toppaload.ai;

import java.util.List;
import java.util.UUID;

public interface AiRecommendationService {

    TaskRecommendationResponse getRecommendationsForTask(UUID taskId);

    List<WorkloadBalanceSuggestionDto> getTeamBalanceSuggestions();

    ApplyBalanceSuggestionResponse applyBalanceSuggestion(ApplyBalanceSuggestionRequest request);
}
