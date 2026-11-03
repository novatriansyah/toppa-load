package com.toppaload.leave;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LeavePortion {
    FULL,
    HALF_AM,
    HALF_PM;

    @JsonCreator
    public static LeavePortion fromString(String value) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        return LeavePortion.valueOf(value.trim().toUpperCase());
    }
}
