package com.toppaload.identity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserSummary(
    UUID id,
    String email,
    String fullName,
    Role role,
    boolean active,
    BigDecimal workHoursPerDay,
    List<Integer> workDays
) {}
