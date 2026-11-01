package com.toppaload.shared;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WibClockTest {
    @Test
    void convertsLateUtcEveningToNextCalendarDateInWib() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-01T18:30:00Z"), ZoneOffset.UTC);
        WibClock wibClock = new WibClock(fixed);
        assertThat(wibClock.todayWib()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(wibClock.nowUtc()).isEqualTo(Instant.parse("2026-11-01T18:30:00Z"));
    }
}
