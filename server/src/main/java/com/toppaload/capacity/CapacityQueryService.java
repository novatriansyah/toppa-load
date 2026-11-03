package com.toppaload.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CapacityQueryService {

    HeatmapResponse getHeatmap(LocalDate from, LocalDate to, Granularity granularity, boolean includeTentative);

    List<DayBreakdownDto> getUserBreakdown(UUID userId, LocalDate from, LocalDate to);

    AllocationPreviewResponse previewAllocation(AllocationPreviewRequest request);
}

