package com.toppaload.calendar;

import java.time.Instant;
import java.util.UUID;

public record CalendarFeedTokenDto(
        UUID id,
        UUID userId,
        String feedUrl,
        String token,
        String scope,
        Instant createdAt,
        Instant lastAccessedAt
) {}

