package com.toppaload.capacity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserHeatmapRow(
    UUID userId,
    String fullName,
    String role,
    BigDecimal workHoursPerDay,
    List<CapacityCell> cells
) {}

