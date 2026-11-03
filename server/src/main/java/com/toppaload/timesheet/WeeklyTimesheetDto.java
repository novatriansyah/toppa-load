package com.toppaload.timesheet;

import java.util.List;

public record WeeklyTimesheetDto(
        TimesheetSubmissionDto submission,
        List<TimeEntryDto> entries,
        List<TaskWorkloadComparisonDto> comparisons
) {}

