package com.toppaload.capacity;

import java.time.LocalDate;
import java.util.List;

public record HeatmapResponse(
    LocalDate from,
    LocalDate to,
    Granularity granularity,
    boolean includeTentative,
    List<LocalDate> periods,
    List<UserHeatmapRow> rows
) {}

