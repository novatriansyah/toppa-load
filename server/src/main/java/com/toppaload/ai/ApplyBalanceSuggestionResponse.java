package com.toppaload.ai;

import java.util.UUID;

public record ApplyBalanceSuggestionResponse(
        boolean success,
        String message,
        UUID allocationId,
        UUID newUserId,
        String newUserName,
        UUID taskId
) {}
