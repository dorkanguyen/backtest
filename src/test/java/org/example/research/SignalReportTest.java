package org.example.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the text tables of {@link SignalReport}, with two made-up days. */
class SignalReportTest {

    private final String text = SignalReport.format(new SignalEvaluator(Duration.ofSeconds(1),
            List.of(Duration.ofSeconds(1), Duration.ofMinutes(1)), 0.3, 2)
            .analyse("AAPL", List.of(LocalDate.of(2025, 12, 8), LocalDate.of(2025, 12, 9)),
                    List.of(samples(), samples())));

    @Test
    void showsEveryGroupAndHorizon() {
        assertThat(text).contains("Correlation between signals", "Horizon 1 s", "Horizon 1 min",
                "up + down");
    }

    @Test
    void showsGoodDaysAndOneColumnPerDay() {
        // "up" was right on both days, "down" on none
        assertThat(text).contains("good days", "12-08", "12-09", "2/2", "0/2");
    }

    @Test
    void showsMissingNumbersAsDash() {
        // both signals never change, so their correlation cannot be calculated
        assertThat(text).contains(" -");
        assertThat(text).doesNotContain("NaN");
    }

    private static SignalSamples samples() {
        return new SignalSamples(List.of("up", "down"), Duration.ofSeconds(1).toNanos(),
                new double[] {100000, 100100}, new double[] {50, 50},
                new double[][] {{1, 1}, {-1, -1}});
    }
}
