package com.toppaload.timesheet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimesheetSubmissionDto(
        UUID id,
        UUID userId,
        String userFullName,
        LocalDate weekStartDate,
        String status,
        Instant submittedAt,
        Instant approvedAt,
        UUID approvedBy,
        String reviewerNote
) {}

