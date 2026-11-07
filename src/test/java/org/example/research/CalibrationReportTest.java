package org.example.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the text table of {@link CalibrationReport}. */
class CalibrationReportTest {

    private final SignalCalibrator calibrator = new SignalCalibrator(
            List.of(Duration.ofSeconds(1), Duration.ofMinutes(1)), List.of(0.3, 0.5, 1.0));

    private final String text = CalibrationReport.format(
            calibrator.calibrate(List.of(day(100100)), "signal"),
            calibrator.calibrate(List.of(day(99900)), "signal"));

    @Test
    void showsTrainingAndTestSideBySide() {
        // the price rose 1 cent on the training day and fell 1 cent on the test day
        assertThat(text).contains("training | test", "1 s", "1 min", "+1.000 | -1.000");
    }

    @Test
    void showsBandsAndMissingNumbersAsDash() {
        // nothing in the band 0.5-1.0, and no price one minute later
        assertThat(text).contains("0.3-0.5", "0.5-1.0", "- | -");
        assertThat(text).doesNotContain("NaN");
    }

    private static SignalSamples day(double secondMid) {
        return new SignalSamples(List.of("signal"), Duration.ofSeconds(1).toNanos(),
                new double[] {100000, secondMid}, new double[] {50, 50},
                new double[][] {{0.4, 0}});
    }
}
