package com.toppaload.timesheet;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimesheetService {

    WeeklyTimesheetDto getWeeklyTimesheet(UUID userId, LocalDate weekStartDate);

    WeeklyTimesheetDto saveWeeklyDraft(UUID userId, SaveWeeklyTimesheetRequest request);

    WeeklyTimesheetDto submitWeeklyTimesheet(UUID userId, SubmitWeeklyTimesheetRequest request);

    TimesheetSubmissionDto approveTimesheet(UUID submissionId, UUID supervisorId);

    TimesheetSubmissionDto returnTimesheet(UUID submissionId, UUID supervisorId, String reviewerNote);

    List<TimesheetSubmissionDto> getPendingApprovals();
}

