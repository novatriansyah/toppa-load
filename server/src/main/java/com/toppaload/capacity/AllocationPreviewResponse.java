package com.toppaload.capacity;

import java.util.List;

public record AllocationPreviewResponse(
    List<CapacityCell> beforeCells,
    List<CapacityCell> afterCells,
    boolean introducesOverload,
    boolean introducesLeaveConflict
) {}

