package com.toppaload.timesheet;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaveWeeklyTimesheetRequest(
        @NotNull LocalDate weekStartDate,
        List<TimeEntryInput> entries
) {
    public record TimeEntryInput(
            UUID id,
            @NotNull UUID projectId,
            UUID taskId,
            @NotNull LocalDate entryDate,
            @NotNull BigDecimal hours,
            String notes
    ) {}
}

