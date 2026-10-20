package org.example.execution;

import java.util.List;
import org.example.marketdata.book.OrderBook;

/**
 * Decides how and when the difference between the current and the target position is traded.
 *
 * <p>The strategy says what it wants to hold; the execution algorithm turns that into
 * instructions: place market or limit orders, cancel waiting ones. It may decide to wait, for
 * example to avoid paying commission for many tiny trades. When one of its orders is filled, it
 * is told with {@link #onFill}, so it can keep track of what is still waiting in the book.
 */
public interface ExecutionAlgo {

    /**
     * Decides what to do after a market event.
     *
     * @param timestamp the time of the current event, nanoseconds since 1970 UTC
     * @param symbol the symbol to trade, for example {@code AAPL}
     * @param currentPosition the number of shares held now; negative for a short position
     * @param targetPosition the number of shares the strategy wants to hold
     * @param book the current order book of the symbol
     * @return the instructions in the order they are carried out; empty to do nothing now
     */
    List<Instruction> decide(long timestamp, String symbol, int currentPosition,
            int targetPosition, OrderBook book);

    /**
     * Tells that one of the algorithm's orders was filled, completely or in part. Does nothing
     * by default, which is fine for algorithms that only send market orders.
     *
     * @param fill the fill; {@link Fill#orderId()} tells which order it belongs to
     */
    default void onFill(Fill fill) {
    }
}
