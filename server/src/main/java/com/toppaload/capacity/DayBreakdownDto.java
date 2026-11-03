package com.toppaload.capacity;

import com.toppaload.allocation.AllocationSummary;
import com.toppaload.leave.LeaveSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DayBreakdownDto(
    LocalDate date,
    int dayOfWeek,
    boolean isWorkday,
    boolean isHoliday,
    String holidayName,
    BigDecimal baseHours,
    BigDecimal leaveHours,
    BigDecimal netCapacityHours,
    BigDecimal confirmedHours,
    BigDecimal tentativeHours,
    UtilizationStatus status,
    boolean hasLeaveConflict,
    List<AllocationSummary> allocations,
    List<LeaveSummary> leaves
) {}

