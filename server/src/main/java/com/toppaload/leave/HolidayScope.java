package com.toppaload.leave;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum HolidayScope {
    NATIONAL,
    COMPANY;

    @JsonCreator
    public static HolidayScope fromString(String value) {
        if (value == null || value.isBlank()) {
            return NATIONAL;
        }
        String upper = value.trim().toUpperCase();
        if ("COMPANY".equals(upper) || "PERUSAHAAN".equals(upper)) {
            return COMPANY;
        }
        return NATIONAL;
    }
}
