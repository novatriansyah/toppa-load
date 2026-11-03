package com.toppaload.leave.internal;

import com.toppaload.leave.LeaveSummary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leaves")
class LeaveController {

    private final LeaveService leaveService;

    LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLeave(@PathVariable UUID id) {
        leaveService.deleteLeave(id);
    }
}

