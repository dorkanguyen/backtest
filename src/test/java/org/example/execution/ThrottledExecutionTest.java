package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.example.order.Order;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/** Tests for {@link ThrottledExecution} with a tolerance of 10 shares and 30 seconds cooldown. */
class ThrottledExecutionTest {

    private static final long SECOND = 1_000_000_000L;

    private final ExecutionAlgo algo = new ThrottledExecutionDefinition()
            .create(Map.of("toleranceShares", 10.0, "cooldownSeconds", 30.0));

    @Test
    void bigDifferenceIsBought() {
        assertThat(decide(0, 26, 40)).contains(Order.market(1, "AAPL", Side.BUY, 14));
    }

    @Test
    void bigDifferenceIsSold() {
        assertThat(decide(0, 50, 30)).contains(Order.market(1, "AAPL", Side.SELL, 20));
    }

    @Test
    void smallDifferenceIsIgnored() {
        assertThat(decide(0, 50, 42)).isEmpty();
    }

    @Test
    void closingThePositionIgnoresTheTolerance() {
        assertThat(decide(0, 5, 0)).contains(Order.market(1, "AAPL", Side.SELL, 5));
    }

    @Test
    void goingFromLongToShortSellsTheWholeDifference() {
        assertThat(decide(0, 30, -20)).contains(Order.market(1, "AAPL", Side.SELL, 50));
    }

    @Test
    void noDifferenceSendsNothing() {
        assertThat(decide(0, 0, 0)).isEmpty();
    }

    @Test
    void secondTradeWaitsForTheCooldown() {
        decide(0, 0, 50);

        assertThat(decide(29, 50, 0)).isEmpty();
        assertThat(decide(30, 50, 0)).contains(Order.market(2, "AAPL", Side.SELL, 50));
    }

    @Test
    void ignoredDifferenceDoesNotStartTheCooldown() {
        decide(0, 50, 45);

        assertThat(decide(1, 50, 0)).contains(Order.market(1, "AAPL", Side.SELL, 50));
    }

    @Test
    void ordersAreNumberedFromOne() {
        assertThat(decide(0, 0, 50)).map(Order::id).contains(1L);
        assertThat(decide(30, 50, 0)).map(Order::id).contains(2L);
        assertThat(decide(60, 0, -50)).map(Order::id).contains(3L);
    }

    @Test
    void zeroCooldownNeverWaits() {
        ExecutionAlgo noWaiting = new ThrottledExecutionDefinition()
                .create(Map.of("cooldownSeconds", 0.0));

        assertThat(noWaiting.decide(0, "AAPL", 0, 50)).isPresent();
        assertThat(noWaiting.decide(0, "AAPL", 50, 0)).isPresent();
    }

    private Optional<Order> decide(long second, int current, int target) {
        return algo.decide(second * SECOND, "AAPL", current, target);
    }
}
