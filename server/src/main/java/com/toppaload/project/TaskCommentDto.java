package com.toppaload.project;

import java.time.Instant;
import java.util.UUID;

public record TaskCommentDto(
        UUID id,
        UUID taskId,
        UUID authorId,
        String authorFullName,
        String authorEmail,
        String commentText,
        Instant createdAt,
        Instant updatedAt
) {}

