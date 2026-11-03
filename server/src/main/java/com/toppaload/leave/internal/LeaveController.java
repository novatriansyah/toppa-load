package com.toppaload.leave.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.leave.LeaveBalanceDto;
import com.toppaload.leave.LeaveSummary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leaves")
class LeaveController {

    private final LeaveService leaveService;
    private final LeaveBalanceService leaveBalanceService;
    private final UserDirectory userDirectory;

    LeaveController(
            LeaveService leaveService,
            LeaveBalanceService leaveBalanceService,
            UserDirectory userDirectory
    ) {
        this.leaveService = leaveService;
        this.leaveBalanceService = leaveBalanceService;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    List<LeaveSummary> listLeaves(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId
    ) {
        return leaveService.listLeaves(from, to, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LeaveSummary createLeave(@Valid @RequestBody LeaveService.CreateLeaveRequest request) {
        return leaveService.createLeave(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    LeaveSummary approveLeave(@PathVariable UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return leaveService.approveLeave(id, actor.userId());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    LeaveSummary rejectLeave(@PathVariable UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        return leaveService.rejectLeave(id, actor.userId());
    }

    @GetMapping("/balances")
    LeaveBalanceDto getBalance(
            @RequestParam(name = "year", defaultValue = "2026") int year,
            @RequestParam(name = "userId", required = false) UUID requestedUserId
    ) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        UUID target = actor.userId();
        if (requestedUserId != null && !requestedUserId.equals(actor.userId())) {
            if (actor.role() == Role.MEMBER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anggota hanya dapat melihat saldo cuti sendiri.");
            }
            target = requestedUserId;
        }
        return leaveBalanceService.getBalance(target, year);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLeave(@PathVariable UUID id) {
        leaveService.deleteLeave(id);
    }
}

