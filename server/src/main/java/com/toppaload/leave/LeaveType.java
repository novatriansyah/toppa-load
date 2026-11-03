package com.toppaload.leave;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LeaveType {
    ANNUAL,
    SICK,
    UNPAID,
    OTHER;

    @JsonCreator
    public static LeaveType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return LeaveType.valueOf(value.trim().toUpperCase());
    }
}
