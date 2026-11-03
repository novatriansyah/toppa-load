package com.toppaload.timesheet.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import com.toppaload.timesheet.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class TimesheetServiceImpl implements TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;

    TimesheetServiceImpl(
            TimesheetRepository timesheetRepository,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder
    ) {
        this.timesheetRepository = timesheetRepository;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        int diff = date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return date.minusDays(diff);
    }

    @Override
    @Transactional
    public WeeklyTimesheetDto getWeeklyTimesheet(UUID userId, LocalDate weekStartDate) {
        LocalDate monday = normalizeToMonday(weekStartDate);
        TimesheetSubmissionDto submission = timesheetRepository.getOrCreateDraftSubmission(userId, monday);
        List<TimeEntryDto> entries = timesheetRepository.findEntriesBySubmissionId(submission.id());
        List<TaskWorkloadComparisonDto> comparisons = timesheetRepository.computeComparisons(userId, monday, monday.plusDays(6));

        return new WeeklyTimesheetDto(submission, entries, comparisons);
    }

    @Override
    @Transactional
    public WeeklyTimesheetDto saveWeeklyDraft(UUID userId, SaveWeeklyTimesheetRequest request) {
        LocalDate monday = normalizeToMonday(request.weekStartDate());
        LocalDate sunday = monday.plusDays(6);

        TimesheetSubmissionDto submission = timesheetRepository.getOrCreateDraftSubmission(userId, monday);
        if ("SUBMITTED".equals(submission.status()) || "APPROVED".equals(submission.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Timesheet berstatus " + submission.status() + " dan tidak dapat diubah.");
        }

        if (request.entries() != null) {
            Map<LocalDate, BigDecimal> hoursByDate = new HashMap<>();
            for (var entry : request.entries()) {
                if (entry.entryDate().isBefore(monday) || entry.entryDate().isAfter(sunday)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Tanggal entri (" + entry.entryDate() + ") berada di luar minggu berjalan.");
                }
                if (entry.hours().compareTo(BigDecimal.ZERO) < 0 || entry.hours().compareTo(new BigDecimal("24.00")) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Jam kerja per entri harus antara 0 dan 24 jam.");
                }
                hoursByDate.merge(entry.entryDate(), entry.hours(), BigDecimal::add);
            }

            for (var totalOnDate : hoursByDate.entrySet()) {
                if (totalOnDate.getValue().compareTo(new BigDecimal("24.00")) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Total jam kerja pada tanggal " + totalOnDate.getKey() + " melebihi batas 24 jam.");
                }
            }
        }

        timesheetRepository.replaceEntries(submission.id(), userId, request.entries());

        List<TimeEntryDto> entries = timesheetRepository.findEntriesBySubmissionId(submission.id());
        List<TaskWorkloadComparisonDto> comparisons = timesheetRepository.computeComparisons(userId, monday, sunday);

        return new WeeklyTimesheetDto(submission, entries, comparisons);
    }

    @Override
    @Transactional
    public WeeklyTimesheetDto submitWeeklyTimesheet(UUID userId, SubmitWeeklyTimesheetRequest request) {
        LocalDate monday = normalizeToMonday(request.weekStartDate());
        TimesheetSubmissionDto submission = timesheetRepository.getOrCreateDraftSubmission(userId, monday);

        if (!"DRAFT".equals(submission.status()) && !"RETURNED".equals(submission.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hanya timesheet berstatus DRAFT atau RETURNED yang dapat diajukan.");
        }

        Instant now = Instant.now();
        timesheetRepository.updateSubmissionStatus(submission.id(), "SUBMITTED", now, null, null, null);

        UserSummary user = userDirectory.findActiveById(userId).orElseThrow();
        auditRecorder.record(
                userId,
                user.email(),
                "SUBMIT",
                "TIMESHEET",
                submission.id().toString(),
                Map.of("status", submission.status()),
                Map.of("status", "SUBMITTED", "weekStartDate", monday.toString())
        );

        return getWeeklyTimesheet(userId, monday);
    }

    @Override
    @Transactional
    public TimesheetSubmissionDto approveTimesheet(UUID submissionId, UUID supervisorId) {
        TimesheetSubmissionDto submission = timesheetRepository.findSubmissionById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timesheet tidak ditemukan."));

        if (!"SUBMITTED".equals(submission.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hanya timesheet berstatus SUBMITTED yang dapat disetujui.");
        }

        Instant now = Instant.now();
        timesheetRepository.updateSubmissionStatus(submissionId, "APPROVED", submission.submittedAt(), now, supervisorId, null);

        UserSummary supervisor = userDirectory.findActiveById(supervisorId).orElseThrow();
        auditRecorder.record(
                supervisorId,
                supervisor.email(),
                "APPROVE",
                "TIMESHEET",
                submissionId.toString(),
                Map.of("status", "SUBMITTED"),
                Map.of("status", "APPROVED", "approvedBy", supervisorId.toString())
        );

        return timesheetRepository.findSubmissionById(submissionId).orElseThrow();
    }

    @Override
    @Transactional
    public TimesheetSubmissionDto returnTimesheet(UUID submissionId, UUID supervisorId, String reviewerNote) {
        TimesheetSubmissionDto submission = timesheetRepository.findSubmissionById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timesheet tidak ditemukan."));

        if (!"SUBMITTED".equals(submission.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hanya timesheet berstatus SUBMITTED yang dapat dikembalikan.");
        }

        timesheetRepository.updateSubmissionStatus(submissionId, "RETURNED", submission.submittedAt(), null, null, reviewerNote);

        UserSummary supervisor = userDirectory.findActiveById(supervisorId).orElseThrow();
        auditRecorder.record(
                supervisorId,
                supervisor.email(),
                "RETURN",
                "TIMESHEET",
                submissionId.toString(),
                Map.of("status", "SUBMITTED"),
                Map.of("status", "RETURNED", "reviewerNote", reviewerNote)
        );

        return timesheetRepository.findSubmissionById(submissionId).orElseThrow();
    }

    @Override
    public List<TimesheetSubmissionDto> getPendingApprovals() {
        return timesheetRepository.findPendingApprovals();
    }
}

