package com.toppaload.timesheet.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.timesheet.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timesheets")
class TimesheetController {

    private final TimesheetService timesheetService;
    private final UserDirectory userDirectory;

    TimesheetController(TimesheetService timesheetService, UserDirectory userDirectory) {
        this.timesheetService = timesheetService;
        this.userDirectory = userDirectory;
    }

    @GetMapping("/weekly")
    public WeeklyTimesheetDto getWeeklyTimesheet(
            @RequestParam("weekStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(name = "userId", required = false) UUID requestedUserId
    ) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        UUID targetUserId = actor.userId();

        if (requestedUserId != null && !requestedUserId.equals(actor.userId())) {
            if (actor.role() != Role.ADMIN && actor.role() != Role.SUPERVISOR) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Hanya Supervisor atau Admin yang dapat melihat timesheet pengguna lain.");
            }
            targetUserId = requestedUserId;
        }

        return timesheetService.getWeeklyTimesheet(targetUserId, weekStartDate);
    }

    @PutMapping("/weekly")
    public WeeklyTimesheetDto saveWeeklyDraft(@Valid @RequestBody SaveWeeklyTimesheetRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return timesheetService.saveWeeklyDraft(actor.userId(), request);
    }

    @PostMapping("/weekly/submit")
    public WeeklyTimesheetDto submitWeeklyTimesheet(@Valid @RequestBody SubmitWeeklyTimesheetRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return timesheetService.submitWeeklyTimesheet(actor.userId(), request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public TimesheetSubmissionDto approveTimesheet(@PathVariable("id") UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return timesheetService.approveTimesheet(id, actor.userId());
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public TimesheetSubmissionDto returnTimesheet(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReturnTimesheetRequest request
    ) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return timesheetService.returnTimesheet(id, actor.userId(), request.reviewerNote());
    }

    @GetMapping("/pending-approvals")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public List<TimesheetSubmissionDto> getPendingApprovals() {
        return timesheetService.getPendingApprovals();
    }
}

