package org.example.execution;

import java.util.Optional;
import org.example.order.Order;

/**
 * Decides how and when the difference between the current and the target position is traded.
 *
 * <p>The strategy says what it wants to hold; the execution algorithm turns that into orders. It
 * may decide to wait, for example to avoid paying commission for many tiny trades.
 */
public interface ExecutionAlgo {

    /**
     * Decides whether to send an order now.
     *
     * @param timestamp the time of the current event, nanoseconds since 1970 UTC
     * @param symbol the symbol to trade, for example {@code AAPL}
     * @param currentPosition the number of shares held now; negative for a short position
     * @param targetPosition the number of shares the strategy wants to hold
     * @return the order to send, or empty to send nothing now
     */
    Optional<Order> decide(long timestamp, String symbol, int currentPosition, int targetPosition);
}
