package com.toppaload.timesheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryDto(
        UUID id,
        UUID submissionId,
        UUID userId,
        UUID projectId,
        String projectCode,
        String projectName,
        UUID taskId,
        String taskTitle,
        LocalDate entryDate,
        BigDecimal hours,
        String notes
) {}

