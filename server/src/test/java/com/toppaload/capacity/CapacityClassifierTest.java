package com.toppaload.capacity;

import com.toppaload.capacity.internal.CapacityClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityClassifierTest {

    @Test
    @DisplayName("TC-01: 30 / 40j -> 75.0% OPTIMAL")
    void tc01() {
        var res = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("30.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("75.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OPTIMAL);
        assertThat(res.hasLeaveConflict()).isFalse();
    }

    @Test
    @DisplayName("TC-02: 20 / 40j -> 50.0% UNDERUTILIZED")
    void tc02() {
        var res = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("20.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("50.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.UNDERUTILIZED);
    }

    @Test
    @DisplayName("TC-03: 24 / 40j -> 60.0% OPTIMAL (inclusive lower bound)")
    void tc03() {
        var res = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("24.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("60.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OPTIMAL);
    }

    @Test
    @DisplayName("TC-04: 40 / 40j -> 100.0% OPTIMAL (inclusive upper bound)")
    void tc04() {
        var res = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("40.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("100.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OPTIMAL);
    }

    @Test
    @DisplayName("TC-05: 42 / 40j -> 105.0% OVERLOADED")
    void tc05() {
        var res = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("42.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("105.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OVERLOADED);
    }

    @Test
    @DisplayName("TC-06: 32 / 32j -> 100.0% OPTIMAL (national holiday on Thursday)")
    void tc06() {
        var res = CapacityClassifier.classify(
            new BigDecimal("32.0"), new BigDecimal("32.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("100.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OPTIMAL);
    }

    @Test
    @DisplayName("TC-07: 40 / 32j -> 125.0% OVERLOADED with leave conflict")
    void tc07() {
        var res = CapacityClassifier.classify(
            new BigDecimal("32.0"), new BigDecimal("40.0"), BigDecimal.ZERO, false, true, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("125.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OVERLOADED);
        assertThat(res.hasLeaveConflict()).isTrue();
    }

    @Test
    @DisplayName("TC-08: 30 / 36j -> 83.3% OPTIMAL (half day leave on Wednesday)")
    void tc08() {
        var res = CapacityClassifier.classify(
            new BigDecimal("36.0"), new BigDecimal("30.0"), BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("83.3");
        assertThat(res.status()).isEqualTo(UtilizationStatus.OPTIMAL);
    }

    @Test
    @DisplayName("TC-09: 8 / 0j -> null% OVERLOADED with leave conflict (divide by zero safe)")
    void tc09() {
        var res = CapacityClassifier.classify(
            new BigDecimal("0.0"), new BigDecimal("8.0"), BigDecimal.ZERO, false, true, null, null
        );
        assertThat(res.utilizationPct()).isNull();
        assertThat(res.status()).isEqualTo(UtilizationStatus.OVERLOADED);
        assertThat(res.hasLeaveConflict()).isTrue();
    }

    @Test
    @DisplayName("TC-10: Confirmed 30j + Tentative 20j: Default 75.0% vs IncludeTentative 125.0%")
    void tc10() {
        // Default (includeTentative = false)
        var defaultRes = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("30.0"), new BigDecimal("20.0"), false, false, null, null
        );
        assertThat(defaultRes.utilizationPct()).isEqualByComparingTo("75.0");
        assertThat(defaultRes.status()).isEqualTo(UtilizationStatus.OPTIMAL);

        // Include tentative = true
        var tentativeRes = CapacityClassifier.classify(
            new BigDecimal("40.0"), new BigDecimal("30.0"), new BigDecimal("20.0"), true, false, null, null
        );
        assertThat(tentativeRes.utilizationPct()).isEqualByComparingTo("125.0");
        assertThat(tentativeRes.status()).isEqualTo(UtilizationStatus.OVERLOADED);
    }

    @Test
    @DisplayName("Zero capacity and zero workload -> 0.0% NO_CAPACITY")
    void zeroCapacityNoWorkload() {
        var res = CapacityClassifier.classify(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, false, null, null
        );
        assertThat(res.utilizationPct()).isEqualByComparingTo("0.0");
        assertThat(res.status()).isEqualTo(UtilizationStatus.NO_CAPACITY);
        assertThat(res.hasLeaveConflict()).isFalse();
    }
}

