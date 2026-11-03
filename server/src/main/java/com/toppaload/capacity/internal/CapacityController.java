package com.toppaload.capacity.internal;

import com.toppaload.capacity.AllocationPreviewRequest;
import com.toppaload.capacity.AllocationPreviewResponse;
import com.toppaload.capacity.CapacityQueryService;
import com.toppaload.capacity.DayBreakdownDto;
import com.toppaload.capacity.Granularity;
import com.toppaload.capacity.HeatmapResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
class CapacityController {

    private final CapacityQueryService capacityQueryService;

    CapacityController(CapacityQueryService capacityQueryService) {
        this.capacityQueryService = capacityQueryService;
    }

    @GetMapping("/api/v1/capacity/heatmap")
    HeatmapResponse getHeatmap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "WEEK") Granularity granularity,
            @RequestParam(defaultValue = "false") boolean includeTentative
    ) {
        return capacityQueryService.getHeatmap(from, to, granularity, includeTentative);
    }

    @GetMapping("/api/v1/capacity/users/{userId}/breakdown")
    List<DayBreakdownDto> getUserBreakdown(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return capacityQueryService.getUserBreakdown(userId, from, to);
    }

    @PostMapping("/api/v1/allocations/preview")
    AllocationPreviewResponse previewAllocation(@Valid @RequestBody AllocationPreviewRequest request) {
        return capacityQueryService.previewAllocation(request);
    }
}

