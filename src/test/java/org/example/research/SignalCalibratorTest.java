package org.example.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import org.example.research.CalibrationTable.Band;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SignalCalibrator}.
 *
 * <p>The hand-made samples, one per second, prices in 1/10000 dollars:
 *
 * <pre>
 * sample        0        1        2        3       4
 * mid      100000   100100   100300   100200     NaN   (10.00, 10.01, 10.03, 10.02, no book)
 * half         50       50      100       50     NaN
 * opinion     0.4      0.8     -0.9      0.2     0.6
 * </pre>
 *
 * <p>Bands 0.3-0.5 and 0.5-1. After 1 second: sample 0 (weak buy) +1 cent; sample 1 (strong
 * buy) +2 cents; sample 2 (strong sell) -1 cent, which is +1 in its direction; sample 3 is too
 * weak; sample 4 has no price.
 */
class SignalCalibratorTest {

    private static final double NAN = Double.NaN;
    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private final SignalCalibrator calibrator = new SignalCalibrator(
            List.of(Duration.ofSeconds(2), Duration.ofSeconds(1)), List.of(0.3, 0.5, 1.0));

    private final CalibrationTable table = calibrator.calibrate(List.of(day(
            new double[] {100000, 100100, 100300, 100200, NAN},
            new double[] {50, 50, 100, 50, NAN},
            new double[] {0.4, 0.8, -0.9, 0.2, 0.6})), "signal");

    @Test
    void weakBandGetsItsOwnMove() {
        Band weak = table.bands().get(0);

        assertThat(weak.from()).isEqualTo(0.3);
        assertThat(weak.to()).isEqualTo(0.5);
        assertThat(weak.cells().get(0).samples()).isEqualTo(1);
        assertThat(weak.cells().get(0).averageMoveCents()).isCloseTo(1, within(1e-12));
    }

    @Test
    void sellingOpinionCountsWithTheMoveTurnedAround() {
        Band strong = table.bands().get(1);

        // +2 cents for the buy at sample 1 and +1 cent for the sell at sample 2
        assertThat(strong.cells().get(0).samples()).isEqualTo(2);
        assertThat(strong.cells().get(0).averageMoveCents()).isCloseTo(1.5, within(1e-12));
    }

    @Test
    void horizonsAreSortedAndLongerOnesCompareWithLaterPrices() {
        assertThat(table.horizons()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        // after 2 seconds: sample 0 +3 cents; sample 1 +1 cent (sample 2 has no later price)
        assertThat(table.bands().get(0).cells().get(1).averageMoveCents())
                .isCloseTo(3, within(1e-12));
        assertThat(table.bands().get(1).cells().get(1).averageMoveCents())
                .isCloseTo(1, within(1e-12));
    }

    @Test
    void halfSpreadIsAveragedPerBand() {
        assertThat(table.bands().get(0).averageHalfSpreadCents()).isCloseTo(0.5, within(1e-12));
        assertThat(table.bands().get(1).averageHalfSpreadCents()).isCloseTo(0.75, within(1e-12));
    }

    @Test
    void emptyBandHasNoNumbers() {
        SignalCalibrator twoBands = new SignalCalibrator(List.of(Duration.ofSeconds(1)),
                List.of(0.0, 0.1, 1.0));
        CalibrationTable onlyWeak = twoBands.calibrate(List.of(day(
                new double[] {100000, 100100}, new double[] {50, 50},
                new double[] {0.5, 0.5})), "signal");
        Band empty = onlyWeak.bands().get(0);

        assertThat(empty.cells().get(0).samples()).isZero();
        assertThat(empty.cells().get(0).averageMoveCents()).isNaN();
        assertThat(empty.averageHalfSpreadCents()).isNaN();
    }

    @Test
    void movesStayInsideTheirDay() {
        // day 1 ends at 10.01 and day 2 starts at 10.00; glued together this would be -1 cent
        CalibrationTable twoDays = calibrator.calibrate(List.of(
                day(new double[] {100000, 100100}, new double[] {50, 50},
                        new double[] {0.4, 0.4}),
                day(new double[] {100000, 100100}, new double[] {50, 50},
                        new double[] {0.4, 0.4})), "signal");

        assertThat(twoDays.bands().get(0).cells().get(0).samples()).isEqualTo(2);
        assertThat(twoDays.bands().get(0).cells().get(0).averageMoveCents())
                .isCloseTo(1, within(1e-12));
    }

    @Test
    void rejectsUnknownSignal() {
        assertThatThrownBy(() -> calibrator.calibrate(List.of(day(new double[] {100000},
                new double[] {50}, new double[] {0.4})), "other"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBandEdgesThatDoNotIncrease() {
        assertThatThrownBy(() -> new SignalCalibrator(List.of(Duration.ofSeconds(1)),
                List.of(0.5, 0.3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SignalCalibrator(List.of(Duration.ofSeconds(1)),
                List.of(0.5, 1.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHorizonThatIsNotWholeSampleIntervals() {
        SignalCalibrator halfSecond = new SignalCalibrator(List.of(Duration.ofMillis(1500)),
                List.of(0.3, 1.0));

        assertThatThrownBy(() -> halfSecond.calibrate(List.of(day(new double[] {100000},
                new double[] {50}, new double[] {0.4})), "signal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SignalSamples day(double[] mids, double[] halfSpreads, double[] opinions) {
        return new SignalSamples(List.of("signal"), SECOND, mids, halfSpreads,
                new double[][] {opinions});
    }
}
