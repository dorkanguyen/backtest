package org.example.execution;

import java.time.Duration;
import java.util.List;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.Side;

/**
 * Trades the whole difference to the target position at once with a market order, but not too
 * often.
 *
 * <p>Two rules keep the number of trades, and so the commission, low. A difference smaller than
 * {@code tolerance} shares is not traded, like a thermostat ignores tiny temperature changes; a
 * target of 0 is an exception, so a position can always be closed. And after a trade, the next
 * one can only come after {@code cooldown}.
 */
public class ThrottledExecution implements ExecutionAlgo {

    private final int tolerance;
    private final long cooldownNanos;
    private long lastTradeTime;
    private boolean traded;
    private long nextOrderId = 1;

    /**
     * Creates the algorithm.
     *
     * @param tolerance the smallest difference in shares that is traded; at least 1
     * @param cooldown the shortest time between two trades; 0 means no waiting
     * @throws IllegalArgumentException if the tolerance is below 1 or the cooldown is negative
     */
    public ThrottledExecution(int tolerance, Duration cooldown) {
        if (tolerance < 1) {
            throw new IllegalArgumentException("tolerance must be at least 1, was " + tolerance);
        }
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative: " + cooldown);
        }
        this.tolerance = tolerance;
        this.cooldownNanos = cooldown.toNanos();
    }

    @Override
    public List<Instruction> decide(long timestamp, String symbol, int currentPosition,
            int targetPosition, OrderBook book) {
        int difference = targetPosition - currentPosition;
        if (difference == 0) {
            return List.of();
        }
        if (Math.abs(difference) < tolerance && targetPosition != 0) {
            return List.of();
        }
        if (traded && timestamp - lastTradeTime < cooldownNanos) {
            return List.of();
        }
        traded = true;
        lastTradeTime = timestamp;
        Side side = difference > 0 ? Side.BUY : Side.SELL;
        Order order = Order.market(nextOrderId++, symbol, side, Math.abs(difference));
        return List.of(new Instruction.Place(order));
    }
}
