package com.toppaload.capacity.internal;

import com.toppaload.capacity.CapacityCell;
import com.toppaload.capacity.UtilizationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class CapacityClassifier {

    public static final BigDecimal DEFAULT_UNDERUTILIZED_THRESHOLD = new BigDecimal("60.0");
    public static final BigDecimal DEFAULT_OVERLOADED_THRESHOLD = new BigDecimal("100.0");

    private CapacityClassifier() {}

    public record ClassificationResult(
        BigDecimal utilizationPct,
        UtilizationStatus status,
        boolean hasLeaveConflict
    ) {}

    public static ClassificationResult classify(
        BigDecimal netCapacityHours,
        BigDecimal confirmedHours,
        BigDecimal tentativeHours,
        boolean includeTentative,
        boolean hasLeaveConflictFlag,
        BigDecimal underutilizedThreshold,
        BigDecimal overloadedThreshold
    ) {
        BigDecimal net = netCapacityHours != null ? netCapacityHours : BigDecimal.ZERO;
        BigDecimal conf = confirmedHours != null ? confirmedHours : BigDecimal.ZERO;
        BigDecimal tent = tentativeHours != null ? tentativeHours : BigDecimal.ZERO;
        BigDecimal under = underutilizedThreshold != null ? underutilizedThreshold : DEFAULT_UNDERUTILIZED_THRESHOLD;
        BigDecimal over = overloadedThreshold != null ? overloadedThreshold : DEFAULT_OVERLOADED_THRESHOLD;

        BigDecimal effective = includeTentative ? conf.add(tent) : conf;

        if (net.compareTo(BigDecimal.ZERO) == 0) {
            if (effective.compareTo(BigDecimal.ZERO) == 0) {
                return new ClassificationResult(
                    BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
                    UtilizationStatus.NO_CAPACITY,
                    false
                );
            } else {
                // C=0 and A > 0 (e.g. TC-09): OVERLOADED, utilizationPct = null, leaveConflict = true
                return new ClassificationResult(
                    null,
                    UtilizationStatus.OVERLOADED,
                    true
                );
            }
        }

        BigDecimal pct = effective.multiply(BigDecimal.valueOf(100))
                .divide(net, 1, RoundingMode.HALF_UP);

        UtilizationStatus status;
        if (pct.compareTo(under) < 0) {
            status = UtilizationStatus.UNDERUTILIZED;
        } else if (pct.compareTo(over) <= 0) {
            status = UtilizationStatus.OPTIMAL;
        } else {
            status = UtilizationStatus.OVERLOADED;
        }

        boolean leaveConflict = (status == UtilizationStatus.OVERLOADED && hasLeaveConflictFlag);

        return new ClassificationResult(pct, status, leaveConflict);
    }

    public static CapacityCell buildCell(
        LocalDate bucketStart,
        LocalDate bucketEnd,
        BigDecimal netCapacityHours,
        BigDecimal leaveHours,
        BigDecimal confirmedHours,
        BigDecimal tentativeHours,
        boolean includeTentative,
        boolean hasLeaveConflictFlag,
        BigDecimal underutilizedThreshold,
        BigDecimal overloadedThreshold
    ) {
        ClassificationResult cr = classify(
            netCapacityHours,
            confirmedHours,
            tentativeHours,
            includeTentative,
            hasLeaveConflictFlag,
            underutilizedThreshold,
            overloadedThreshold
        );

        return new CapacityCell(
            bucketStart,
            bucketEnd,
            netCapacityHours != null ? netCapacityHours.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
            leaveHours != null ? leaveHours.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
            confirmedHours != null ? confirmedHours.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
            tentativeHours != null ? tentativeHours.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
            cr.utilizationPct(),
            cr.status(),
            cr.hasLeaveConflict()
        );
    }
}
