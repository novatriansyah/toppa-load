package com.toppaload.capacity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CapacityCell(
    LocalDate bucketStart,
    LocalDate bucketEnd,
    BigDecimal netCapacityHours,
    BigDecimal leaveHours,
    BigDecimal confirmedHours,
    BigDecimal tentativeHours,
    BigDecimal utilizationPct,
    UtilizationStatus status,
    boolean hasLeaveConflict
) {}

