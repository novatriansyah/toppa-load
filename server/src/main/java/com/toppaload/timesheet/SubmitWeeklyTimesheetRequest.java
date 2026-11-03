package com.toppaload.timesheet;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SubmitWeeklyTimesheetRequest(
        @NotNull LocalDate weekStartDate
) {}

