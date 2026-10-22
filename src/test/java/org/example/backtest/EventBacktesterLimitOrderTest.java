package org.example.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.example.execution.ExecutionAlgo;
import org.example.execution.Fill;
import org.example.execution.Instruction;
import org.example.execution.Liquidity;
import org.example.execution.PerShareCommission;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.ListMarketEventStore;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.state.SymbolState;
import org.example.order.Order;
import org.example.order.Side;
import org.example.strategy.EventStrategy;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link EventBacktester} with an algorithm that places one limit buy of 100 shares at
 * 10.00 when trading starts. Commission: 0.005 per share, at least 1.00.
 *
 * <pre>
 * 09:00  bid 10.00 x 100 (order 1), ask 10.02 x 1000 (order 2)
 * 09:31  bid 9.99 x 10                     (we join the 10.00 queue behind order 1)
 * 09:32  order 1 trades 100                (nobody is ahead of us any more)
 * 09:33  bid 10.00 x 500 (order 4)         (behind us)
 * 09:34  order 4 trades 60                 (we get 60)
 * 09:35  order 4 trades 100                (we get the last 40)
 * 10:00  the position is sold at the bid 10.00
 * </pre>
 */
class EventBacktesterLimitOrderTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2025, 12, 9, 0, 0);

    private final List<MarketEvent> events = List.of(
            event(9, 0, EventType.ADD, 1, BookSide.BID, 100000, 100),
            event(9, 0, EventType.ADD, 2, BookSide.ASK, 100200, 1000),
            event(9, 31, EventType.ADD, 3, BookSide.BID, 99900, 10),
            event(9, 32, EventType.EXECUTE, 1, BookSide.BID, 100000, 100),
            event(9, 33, EventType.ADD, 4, BookSide.BID, 100000, 500),
            event(9, 34, EventType.EXECUTE, 4, BookSide.BID, 100000, 60),
            event(9, 35, EventType.EXECUTE, 4, BookSide.BID, 100000, 100));

    private final OneLimitBuy algo = new OneLimitBuy();

    private final EventBacktestResult result = new EventBacktester(
            new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00")),
            Duration.ofMinutes(1))
            .run(new ListMarketEventStore(events), "AAPL",
                    new TradingPeriod(DAY.withHour(9), DAY.withHour(9).withMinute(30),
                            DAY.withHour(10)),
                    new NoStrategy(), algo, new BigDecimal("10000"));

    @Test
    void limitOrderIsFilledInTwoPartsAndClosedAtTheEnd() {
        List<Fill> fills = result.fills();

        assertThat(fills).extracting(Fill::quantity).containsExactly(60, 40, 100);
        assertThat(fills).extracting(Fill::liquidity)
                .containsExactly(Liquidity.MAKER, Liquidity.MAKER, Liquidity.TAKER);
        assertThat(fills).extracting(Fill::orderId)
                .containsExactly(1L, 1L, EventBacktester.CLOSING_ORDER_ID);
        assertThat(fills.get(0).price()).isEqualByComparingTo("10.00");
        assertThat(fills.get(0).time()).isEqualTo(DAY.withHour(9).withMinute(34));
        assertThat(fills.get(2).side()).isEqualTo(Side.SELL);
        assertThat(fills.get(2).price()).isEqualByComparingTo("10.00");
    }

    @Test
    void pnlIsOnlyTheCommission() {
        // bought and sold at 10.00; the minimum of 1.00 is paid once by each of the two orders
        List<Fill> fills = result.fills();
        assertThat(fills.get(0).commission()).isEqualByComparingTo("1.00");
        assertThat(fills.get(1).commission()).isEqualByComparingTo("0");
        assertThat(fills.get(2).commission()).isEqualByComparingTo("1.00");
        assertThat(result.pnl()).isEqualByComparingTo("-2.00");
    }

    @Test
    void algorithmIsToldAboutItsOwnFillsOnly() {
        assertThat(algo.fills).extracting(Fill::quantity).containsExactly(60, 40);
    }

    private static MarketEvent event(int hour, int minute, EventType type, long orderId,
            BookSide side, long price, long quantity) {
        long nanos = EventBacktester.toNanos(DAY.withHour(hour).withMinute(minute));
        return new MarketEvent(nanos, "AAPL", type, orderId, side, price, quantity);
    }

    /** Places one limit buy at the first chance and remembers the fills it is told about. */
    private static final class OneLimitBuy implements ExecutionAlgo {

        private final List<Fill> fills = new ArrayList<>();
        private boolean placed;

        @Override
        public List<Instruction> decide(long timestamp, String symbol, int currentPosition,
                int targetPosition, OrderBook book) {
            if (placed) {
                return List.of();
            }
            placed = true;
            return List.of(new Instruction.Place(Order.limit(1, symbol, Side.BUY, 100, 100000)));
        }

        @Override
        public void onFill(Fill fill) {
            fills.add(fill);
        }
    }

    /** The target position does not matter here: the algorithm ignores it. */
    private static final class NoStrategy implements EventStrategy {

        @Override
        public void update(MarketEvent event, SymbolState state) {
        }

        @Override
        public int targetPosition(int currentPosition) {
            return 0;
        }
    }
}
