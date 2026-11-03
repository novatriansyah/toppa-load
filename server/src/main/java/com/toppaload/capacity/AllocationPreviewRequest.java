package com.toppaload.capacity;

import com.toppaload.allocation.AllocationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AllocationPreviewRequest(
    @NotNull(message = "ID pengguna wajib diisi")
    UUID userId,

    @NotNull(message = "Tanggal mulai wajib diisi")
    LocalDate startDate,

    @NotNull(message = "Tanggal selesai wajib diisi")
    LocalDate endDate,

    @NotNull(message = "Jam per hari wajib diisi")
    @DecimalMin(value = "0.01", message = "Jam per hari harus > 0")
    @DecimalMax(value = "24.00", message = "Jam per hari maksimal 24")
    BigDecimal hoursPerDay,

    AllocationType allocationType,

    UUID excludeAllocationId
) {}

