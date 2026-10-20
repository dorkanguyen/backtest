package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExecutionSimulator}. The book: bid 9.00 x 300 (order 1), ask 10.00 x 500
 * (order 10). Commission: 0.01 per share, no minimum, 0.003 taker fee, 0.002 maker rebate.
 */
class ExecutionSimulatorTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2025, 12, 9, 10, 0);
    private static final long BID = 90000;

    private final OrderBook book = new OrderBook("AAPL");
    private final ExecutionSimulator simulator = new ExecutionSimulator(new TieredCommission(
            new BigDecimal("0.01"), BigDecimal.ZERO, new BigDecimal("0.003"), BigDecimal.ZERO,
            new BigDecimal("0.002")));

    ExecutionSimulatorTest() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, BID, 300));
        book.apply(event(EventType.ADD, 10, BookSide.ASK, 100000, 500));
    }

    @Test
    void marketOrderIsFilledAtOnceAsTaker() {
        List<Fill> fills = place(Order.market(7, "AAPL", Side.BUY, 100));

        assertThat(fills).hasSize(1);
        Fill fill = fills.get(0);
        assertThat(fill.orderId()).isEqualTo(7);
        assertThat(fill.price()).isEqualByComparingTo("10.00");
        assertThat(fill.liquidity()).isEqualTo(Liquidity.TAKER);
        // 100 x (0.01 + 0.003)
        assertThat(fill.commission()).isEqualByComparingTo("1.30");
        assertThat(simulator.waitingOrders()).isZero();
    }

    @Test
    void limitOrderWaitsAndIsFilledLaterAsMaker() {
        assertThat(place(Order.limit(7, "AAPL", Side.BUY, 100, BID))).isEmpty();
        assertThat(simulator.waitingOrders()).isEqualTo(1);

        // order 1 is ahead of us; after it is gone, order 2 arrives behind us and trades
        assertThat(onEvent(EventType.EXECUTE, 1, BID, 300)).isEmpty();
        onEvent(EventType.ADD, 2, BID, 200);
        List<Fill> fills = onEvent(EventType.EXECUTE, 2, BID, 60);

        assertThat(fills).hasSize(1);
        Fill fill = fills.get(0);
        assertThat(fill.orderId()).isEqualTo(7);
        assertThat(fill.quantity()).isEqualTo(60);
        assertThat(fill.price()).isEqualByComparingTo("9.00");
        assertThat(fill.liquidity()).isEqualTo(Liquidity.MAKER);
        // 60 x (0.01 - 0.002)
        assertThat(fill.commission()).isEqualByComparingTo("0.48");
        assertThat(fill.time()).isEqualTo(TIME);
        assertThat(simulator.waitingOrders()).isEqualTo(1);
    }

    @Test
    void completelyFilledOrderStopsWaiting() {
        place(Order.limit(7, "AAPL", Side.BUY, 100, BID));
        onEvent(EventType.EXECUTE, 1, BID, 300);
        onEvent(EventType.ADD, 2, BID, 200);

        assertThat(onEvent(EventType.EXECUTE, 2, BID, 150)).extracting(Fill::quantity)
                .containsExactly(100);
        assertThat(simulator.waitingOrders()).isZero();
        assertThat(onEvent(EventType.EXECUTE, 2, BID, 50)).isEmpty();
    }

    @Test
    void cancelledOrderIsNeverFilled() {
        place(Order.limit(7, "AAPL", Side.BUY, 100, BID));

        assertThat(simulator.carryOut(new Instruction.Cancel(7), book, TIME)).isEmpty();
        assertThat(simulator.waitingOrders()).isZero();
        onEvent(EventType.EXECUTE, 1, BID, 300);
        onEvent(EventType.ADD, 2, BID, 200);
        assertThat(onEvent(EventType.EXECUTE, 2, BID, 200)).isEmpty();
    }

    @Test
    void cancellingAnUnknownOrderDoesNothing() {
        assertThat(simulator.carryOut(new Instruction.Cancel(99), book, TIME)).isEmpty();
    }

    @Test
    void cancelAllRemovesEveryWaitingOrder() {
        place(Order.limit(7, "AAPL", Side.BUY, 100, BID));
        place(Order.limit(8, "AAPL", Side.SELL, 100, 100000));

        simulator.cancelAll();

        assertThat(simulator.waitingOrders()).isZero();
    }

    @Test
    void waitingOrderIdCannotBeUsedAgain() {
        place(Order.limit(7, "AAPL", Side.BUY, 100, BID));

        assertThatThrownBy(() -> place(Order.limit(7, "AAPL", Side.BUY, 50, BID)))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<Fill> place(Order order) {
        return simulator.carryOut(new Instruction.Place(order), book, TIME);
    }

    /** Applies an event of the bid side to the book and to the simulator. */
    private List<Fill> onEvent(EventType type, long orderId, long price, long quantity) {
        MarketEvent event = event(type, orderId, BookSide.BID, price, quantity);
        book.apply(event);
        return simulator.onEvent(event, TIME);
    }

    private static MarketEvent event(
            EventType type, long orderId, BookSide side, long price, long quantity) {
        return new MarketEvent(0, "AAPL", type, orderId, side, price, quantity);
    }
}
