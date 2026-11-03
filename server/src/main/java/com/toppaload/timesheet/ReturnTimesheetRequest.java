package com.toppaload.timesheet;

import jakarta.validation.constraints.NotBlank;

public record ReturnTimesheetRequest(
        @NotBlank String reviewerNote
) {}

