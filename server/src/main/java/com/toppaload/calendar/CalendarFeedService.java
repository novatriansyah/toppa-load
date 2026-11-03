package com.toppaload.calendar;

import java.util.UUID;

public interface CalendarFeedService {

    CalendarFeedTokenDto getOrCreateToken(UUID userId, String baseUrl);

    CalendarFeedTokenDto rotateToken(UUID userId, String baseUrl);

    String generateICalendarFeed(String token);
}

