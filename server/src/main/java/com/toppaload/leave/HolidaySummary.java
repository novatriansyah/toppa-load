package com.toppaload.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HolidaySummary(
    UUID id,
    LocalDate holidayDate,
    String name,
    HolidayScope scope,
    Instant createdAt
) {}
