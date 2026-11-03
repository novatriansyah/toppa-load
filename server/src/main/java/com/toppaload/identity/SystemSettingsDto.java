package com.toppaload.identity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SystemSettingsDto(
        @NotNull @DecimalMin("0.0") @DecimalMax("999.9") BigDecimal underutilizedThresholdPct,
        @NotNull @DecimalMin("0.1") @DecimalMax("999.9") BigDecimal overloadedThresholdPct,
        @NotBlank String workTimezone,
        String teamsWebhookUrl
) {
    public SystemSettingsDto(BigDecimal underutilizedThresholdPct, BigDecimal overloadedThresholdPct, String workTimezone) {
        this(underutilizedThresholdPct, overloadedThresholdPct, workTimezone, null);
    }
}
