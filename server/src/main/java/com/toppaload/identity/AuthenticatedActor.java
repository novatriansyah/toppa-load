package com.toppaload.identity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AuthenticatedActor(
    UUID userId,
    String email,
    String fullName,
    Role role,
    BigDecimal workHoursPerDay,
    List<Integer> workDays
) {}
