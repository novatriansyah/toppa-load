package com.toppaload.ai.internal;

import com.toppaload.ai.AiRecommendationService;
import com.toppaload.ai.ApplyBalanceSuggestionRequest;
import com.toppaload.ai.ApplyBalanceSuggestionResponse;
import com.toppaload.ai.TaskRecommendationResponse;
import com.toppaload.ai.WorkloadBalanceSuggestionDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/balance-suggestions/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApplyBalanceSuggestionResponse> applyBalanceSuggestion(
            @Valid @RequestBody ApplyBalanceSuggestionRequest request
    ) {
        return ResponseEntity.ok(aiRecommendationService.applyBalanceSuggestion(request));
    }
}
