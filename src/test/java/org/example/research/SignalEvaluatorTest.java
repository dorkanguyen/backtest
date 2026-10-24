package org.example.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.backtest.EventBacktester;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.ListMarketEventStore;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.research.SignalEvaluationReport.GroupResult;
import org.example.research.SignalEvaluationReport.HorizonResult;
import org.example.signal.Signal;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SignalEvaluator}.
 *
 * <p>The hand-made samples, one per second, prices in 1/10000 dollars:
 *
 * <pre>
 * sample        0        1        2        3       4
 * mid      100000   100100   100100   100000     NaN   (10.00, 10.01, 10.01, 10.00, no book)
 * up            1        0     -0.5      0.2       0
 * wrong        -1        0      0.5     -0.2       0
 * </pre>
 *
 * <p>After 1 second the mid moves +1, 0, -1 cent (sample 3 has no later price). "up" is active
 * (stronger than 0.3) at samples 0 and 2 and right both times; "wrong" is always the opposite.
 */
class SignalEvaluatorTest {

    private static final double NAN = Double.NaN;

    private final SignalEvaluator evaluator = new SignalEvaluator(Duration.ofSeconds(1),
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), 0.3);

    private final SignalEvaluationReport report = evaluator.analyse("AAPL", new SignalSamples(
            List.of("up", "wrong"), Duration.ofSeconds(1).toNanos(),
            new double[] {100000, 100100, 100100, 100000, NAN},
            new double[] {50, 50, 100, 50, NAN},
            new double[][] {{1, 0, -0.5, 0.2, 0}, {-1, 0, 0.5, -0.2, 0}}));

    @Test
    void groupsComeAloneThenInPairsThenAllTogether() {
        assertThat(SignalEvaluator.groups(3)).containsExactly(
                List.of(0), List.of(1), List.of(2),
                List.of(0, 1), List.of(0, 2), List.of(1, 2),
                List.of(0, 1, 2));
    }

    @Test
    void correlationIsOneForTheSameShapeAndMinusOneForTheOpposite() {
        double[] x = {1, 2, 3, 4};

        assertThat(SignalEvaluator.correlation(x, new double[] {10, 20, 30, 40}, 4))
                .isCloseTo(1, within(1e-12));
        assertThat(SignalEvaluator.correlation(x, new double[] {4, 3, 2, 1}, 4))
                .isCloseTo(-1, within(1e-12));
        assertThat(SignalEvaluator.correlation(x, new double[] {5, 5, 5, 5}, 4)).isNaN();
    }

    @Test
    void signalThatIsAlwaysRightHasFullHitRate() {
        HorizonResult oneSecond = group("up").horizons().get(0);

        assertThat(oneSecond.horizon()).isEqualTo(Duration.ofSeconds(1));
        assertThat(oneSecond.activeSamples()).isEqualTo(2);
        assertThat(oneSecond.hitRate()).isEqualTo(1);
        assertThat(oneSecond.averageMoveCents()).isCloseTo(1, within(1e-12));
        assertThat(oneSecond.informationCoefficient()).isPositive();
    }

    @Test
    void signalThatIsAlwaysWrongLosesOnAverage() {
        HorizonResult oneSecond = group("wrong").horizons().get(0);

        assertThat(oneSecond.hitRate()).isEqualTo(0);
        assertThat(oneSecond.averageMoveCents()).isCloseTo(-1, within(1e-12));
        assertThat(oneSecond.informationCoefficient()).isNegative();
    }

    @Test
    void oppositeSignalsCancelOutWhenMixed() {
        HorizonResult oneSecond = group("up", "wrong").horizons().get(0);

        assertThat(oneSecond.activeSamples()).isZero();
        assertThat(oneSecond.hitRate()).isNaN();
        assertThat(oneSecond.averageMoveCents()).isNaN();
        assertThat(oneSecond.informationCoefficient()).isNaN();
    }

    @Test
    void longerHorizonComparesWithLaterPrice() {
        // after 2 seconds: sample 0 +1 cent, sample 1 -1 cent, samples 2 and 3 have no price
        HorizonResult twoSeconds = group("up").horizons().get(1);

        assertThat(twoSeconds.horizon()).isEqualTo(Duration.ofSeconds(2));
        assertThat(twoSeconds.activeSamples()).isEqualTo(1);
        assertThat(twoSeconds.averageMoveCents()).isCloseTo(1, within(1e-12));
    }

    @Test
    void signalsWithOppositeOpinionsHaveCorrelationMinusOne() {
        assertThat(report.correlations().get(0).get(0)).isCloseTo(1, within(1e-12));
        assertThat(report.correlations().get(0).get(1)).isCloseTo(-1, within(1e-12));
    }

    @Test
    void halfSpreadIsAveragedOverSamplesWithBook() {
        // (50 + 50 + 100 + 50) / 4 = 62.5 units = 0.625 cents
        assertThat(report.sampleCount()).isEqualTo(4);
        assertThat(report.averageHalfSpreadCents()).isCloseTo(0.625, within(1e-12));
    }

    /**
     * Replays a small day and checks that a sample sees the market just before the events of
     * its own moment.
     *
     * <pre>
     * 09:29:00    bid 10.00, ask 10.02              (warm-up)
     * 09:30:01    bid 10.01 arrives                 (exactly at sample 1: not seen yet)
     * 09:30:02.5  ask 10.015 arrives
     * samples     0: mid 10.01   1: 10.01   2: 10.015   3: 10.0125
     * opinion     0              0          1           1   (1 once the bid is above 10.00)
     * </pre>
     */
    @Test
    void sampleSeesTheMarketJustBeforeItsOwnEvents() {
        LocalDateTime day = LocalDateTime.of(2025, 12, 9, 9, 30);
        List<MarketEvent> events = List.of(
                event(day.minusMinutes(1), 1, BookSide.BID, 100000),
                event(day.minusMinutes(1), 2, BookSide.ASK, 100200),
                event(day.plusSeconds(1), 3, BookSide.BID, 100100),
                event(day.plusSeconds(2).plusNanos(500_000_000), 4, BookSide.ASK, 100150));
        SignalEvaluator oneSecond = new SignalEvaluator(Duration.ofSeconds(1),
                List.of(Duration.ofSeconds(1)), 0.3);

        SignalEvaluationReport replayed = oneSecond.evaluate(new ListMarketEventStore(events),
                "AAPL", new TradingPeriod(day.minusMinutes(1), day, day.plusSeconds(4)),
                Map.of("bidAboveTen", new BidAboveTenSignal()));

        // half spreads 100, 100, 50, 25 units: average 68.75 units = 0.6875 cents
        assertThat(replayed.sampleCount()).isEqualTo(4);
        assertThat(replayed.averageHalfSpreadCents()).isCloseTo(0.6875, within(1e-12));
        // only sample 2 is active, and the mid then falls by 0.25 cent
        HorizonResult result = replayed.groups().get(0).horizons().get(0);
        assertThat(result.activeSamples()).isEqualTo(1);
        assertThat(result.hitRate()).isEqualTo(0);
        assertThat(result.averageMoveCents()).isCloseTo(-0.25, within(1e-12));
    }

    @Test
    void rejectsHorizonThatIsNotWholeSampleIntervals() {
        assertThatThrownBy(() -> new SignalEvaluator(Duration.ofSeconds(2),
                List.of(Duration.ofSeconds(3)), 0.3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsThresholdOfOne() {
        assertThatThrownBy(() -> new SignalEvaluator(Duration.ofSeconds(1),
                List.of(Duration.ofSeconds(1)), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptySignals() {
        assertThatThrownBy(() -> evaluator.evaluate(new ListMarketEventStore(List.of()), "AAPL",
                new TradingPeriod(LocalDateTime.of(2025, 12, 9, 9, 0),
                        LocalDateTime.of(2025, 12, 9, 9, 30),
                        LocalDateTime.of(2025, 12, 9, 10, 0)),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GroupResult group(String... signals) {
        return report.groups().stream()
                .filter(group -> group.signals().equals(List.of(signals)))
                .findFirst()
                .orElseThrow();
    }

    private static MarketEvent event(LocalDateTime time, long orderId, BookSide side,
            long price) {
        return new MarketEvent(EventBacktester.toNanos(time), "AAPL", EventType.ADD, orderId,
                side, price, 100);
    }

    /** Says "buy" once the best bid is above 10.00. */
    private static final class BidAboveTenSignal implements Signal {

        private double conviction;

        @Override
        public void update(MarketEvent event, SymbolState state) {
            conviction = state.book().bestBid().get().price() > 100000 ? 1 : 0;
        }

        @Override
        public double conviction() {
            return conviction;
        }
    }
}
