package com.toppaload.ai.internal;

import com.toppaload.ai.AiRecommendationService;
import com.toppaload.ai.TaskRecommendationResponse;
import com.toppaload.ai.WorkloadBalanceSuggestionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
class AiController {

    private final AiRecommendationService aiRecommendationService;

    AiController(AiRecommendationService aiRecommendationService) {
        this.aiRecommendationService = aiRecommendationService;
    }

    @GetMapping("/recommend-assignee")
    public ResponseEntity<TaskRecommendationResponse> getAssigneeRecommendations(@RequestParam UUID taskId) {
        return ResponseEntity.ok(aiRecommendationService.getRecommendationsForTask(taskId));
    }

    @GetMapping("/balance-suggestions")
    public ResponseEntity<List<WorkloadBalanceSuggestionDto>> getTeamBalanceSuggestions() {
        return ResponseEntity.ok(aiRecommendationService.getTeamBalanceSuggestions());
    }
}

