package org.example.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.execution.ExecutionAlgo;
import org.example.execution.Fill;
import org.example.execution.PerShareCommission;
import org.example.execution.ThrottledExecutionDefinition;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.ListMarketEventStore;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.order.Side;
import org.example.strategy.EventStrategy;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EventBacktester}: a strategy that always wants 100 shares, on a small made-up
 * day. Warm-up from 09:00, trading from 09:30, closing at 10:00, equity every minute.
 *
 * <pre>
 * 09:00  bid 10.00 x 1000, ask 10.02 x 1000   (warm-up: no trading yet)
 * 09:31  bid 10.01 x 10                       (first event of trading: buy 100 at 10.02)
 * 09:44  ask 10.10 x 1000
 * 09:45  the 10.02 ask is deleted             (best ask 10.10)
 * 09:46  bid 10.08 x 1000                     (at 10:00 the position is sold at 10.08)
 * </pre>
 */
class EventBacktesterTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2025, 12, 9, 0, 0);

    private final List<MarketEvent> events = List.of(
            event(9, 0, EventType.ADD, 1, BookSide.BID, 100000, 1000),
            event(9, 0, EventType.ADD, 2, BookSide.ASK, 100200, 1000),
            event(9, 31, EventType.ADD, 3, BookSide.BID, 100100, 10),
            event(9, 44, EventType.ADD, 4, BookSide.ASK, 101000, 1000),
            event(9, 45, EventType.DELETE, 2, BookSide.ASK, 100200, 1000),
            event(9, 46, EventType.ADD, 5, BookSide.BID, 100800, 1000));

    private final EventBacktestResult result = new EventBacktester(
            new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00")),
            Duration.ofMinutes(1))
            .run(new ListMarketEventStore(events), "AAPL",
                    new TradingPeriod(DAY.withHour(9), DAY.withHour(9).withMinute(30),
                            DAY.withHour(10)),
                    new FixedTargetStrategy(100), noThrottling(), new BigDecimal("10000"));

    @Test
    void buysWhenTradingStartsAndSellsAtTheEnd() {
        List<Fill> fills = result.fills();

        assertThat(fills).hasSize(2);
        assertThat(fills.get(0).side()).isEqualTo(Side.BUY);
        assertThat(fills.get(0).price()).isEqualByComparingTo("10.02");
        assertThat(fills.get(0).time()).isEqualTo(DAY.withHour(9).withMinute(31));
        assertThat(fills.get(1).side()).isEqualTo(Side.SELL);
        assertThat(fills.get(1).price()).isEqualByComparingTo("10.08");
        assertThat(fills.get(1).time()).isEqualTo(DAY.withHour(10));
    }

    @Test
    void pnlIsThePriceGainMinusCommission() {
        // 100 x (10.08 - 10.02) - 2 x 1.00 commission = 4.00
        assertThat(result.pnl()).isEqualByComparingTo("4.00");
        assertThat(result.finalEquity()).isEqualByComparingTo("10004.00");
        assertThat(result.totalCommission()).isEqualByComparingTo("2.00");
    }

    @Test
    void closingCostIsTheLargestDrawdown() {
        // right after buying: 8997 + 100 x mid 10.015 = 9998.50, 1.50 below the start;
        // from 09:46: 8997 + 100 x mid 10.09 = 10006 is the peak; selling at the bid 10.08 and
        // paying 1 dollar commission ends at 10004, which is 2.00 below the peak
        assertThat(result.maxDrawdown()).isEqualByComparingTo("2.00");
    }

    @Test
    void equityIsSampledEveryMinuteAndAtTheEnd() {
        List<EquityPoint> curve = result.equityCurve();

        // 09:30, 09:31, ..., 09:59 and 10:00
        assertThat(curve).hasSize(31);
        assertThat(curve.get(0).time()).isEqualTo(DAY.withHour(9).withMinute(30));
        assertThat(curve.get(0).equity()).isEqualByComparingTo("10000");
        assertThat(curve.get(30).time()).isEqualTo(DAY.withHour(10));
    }

    @Test
    void progressGrowsToOne() {
        List<Double> reports = new ArrayList<>();
        new EventBacktester(
                new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00")),
                Duration.ofMinutes(1))
                .run(new ListMarketEventStore(events), "AAPL",
                        new TradingPeriod(DAY.withHour(9), DAY.withHour(9).withMinute(30),
                                DAY.withHour(10)),
                        new FixedTargetStrategy(100), noThrottling(), new BigDecimal("10000"),
                        reports::add);

        // the replay runs from 09:00 to 10:00; the second event comes at 09:31
        assertThat(reports).first().isEqualTo(0.0);
        assertThat(reports).isSorted();
        assertThat(reports).last().isEqualTo(1.0);
        assertThat(reports.get(1)).isCloseTo(31.0 / 60, within(1e-9));
    }

    private static ExecutionAlgo noThrottling() {
        return new ThrottledExecutionDefinition()
                .create(Map.of("toleranceShares", 1.0, "cooldownSeconds", 0.0));
    }

    private static MarketEvent event(int hour, int minute, EventType type, long orderId,
            BookSide side, long price, long quantity) {
        long nanos = EventBacktester.toNanos(DAY.withHour(hour).withMinute(minute));
        return new MarketEvent(nanos, "AAPL", type, orderId, side, price, quantity);
    }

    /** A strategy that always wants the same position. */
    private record FixedTargetStrategy(int target) implements EventStrategy {

        @Override
        public void update(MarketEvent event, SymbolState state) {
        }

        @Override
        public int targetPosition(int currentPosition) {
            return target;
        }
    }
}
