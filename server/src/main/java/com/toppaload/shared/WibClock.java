package com.toppaload.shared;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class WibClock {

    public static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    private final Clock clock;

    public WibClock() {
        this(Clock.systemUTC());
    }

    public WibClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Instant nowUtc() {
        return clock.instant();
    }

    public LocalDate todayWib() {
        return clock.instant().atZone(WIB).toLocalDate();
    }

    public LocalDate today() {
        return todayWib();
    }

    public Clock getClock() {
        return clock;
    }
}
