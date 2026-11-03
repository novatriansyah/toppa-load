package com.toppaload.allocation.internal;

import com.toppaload.allocation.AllocationSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/allocations")
class AllocationController {

    private final AllocationService allocationService;

    AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping
    List<AllocationSummary> listAllocations(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return allocationService.listAllocations(userId, projectId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AllocationSummary createAllocation(@Valid @RequestBody AllocationService.CreateAllocationRequest request) {
        return allocationService.createAllocation(request);
    }

    @PutMapping("/{id}")
    AllocationSummary updateAllocation(
            @PathVariable UUID id,
            @Valid @RequestBody AllocationService.UpdateAllocationRequest request
    ) {
        return allocationService.updateAllocation(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAllocation(@PathVariable UUID id) {
        allocationService.deleteAllocation(id);
    }
}

