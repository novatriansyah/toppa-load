package com.toppaload.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveSummary(
    UUID id,
    UUID userId,
    String userEmail,
    String userFullName,
    LeaveType leaveType,
    LeavePortion portion,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    String notes,
    Instant createdAt
) {}
