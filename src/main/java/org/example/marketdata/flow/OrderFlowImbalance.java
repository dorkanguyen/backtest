package org.example.marketdata.flow;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;

/**
 * Order flow imbalance (OFI): how much buying pressure was added to the best prices of the book
 * in a sliding time window, minus the selling pressure.
 *
 * <p>After every event the best bid and ask are compared with the previous ones. On the bid side
 * more shares or a higher price add buying pressure, fewer shares or a lower price take it away.
 * On the ask side more shares or a lower price add selling pressure. The changes are summed over
 * the window. A positive value means buyers got stronger, a negative value means sellers did.
 * Based on Cont, Kukanov and Stoikov, "The Price Impact of Order Book Events" (2014).
 */
public class OrderFlowImbalance {

    private final String symbol;
    private final long windowNanos;
    private final Deque<Change> changes = new ArrayDeque<>();
    private long value;
    private Optional<PriceLevel> previousBid = Optional.empty();
    private Optional<PriceLevel> previousAsk = Optional.empty();

    /**
     * Creates an empty order flow imbalance.
     *
     * @param symbol the symbol this belongs to, for example {@code AAPL}
     * @param window how far back changes are counted, for example 10 seconds
     * @throws IllegalArgumentException if the window is not positive
     */
    public OrderFlowImbalance(String symbol, Duration window) {
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Window must be positive: " + window);
        }
        this.symbol = symbol;
        this.windowNanos = window.toNanos();
    }

    /**
     * Updates the imbalance with the book after one event.
     *
     * @param event the event that was just applied to the book
     * @param book the order book of the symbol, already updated with {@code event}
     * @throws IllegalArgumentException if the event belongs to another symbol
     */
    public void apply(MarketEvent event, OrderBook book) {
        if (!event.symbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "Event of " + event.symbol() + " applied to order flow of " + symbol);
        }
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        long change = bidChange(previousBid, bid) - askChange(previousAsk, ask);
        if (change != 0) {
            changes.addLast(new Change(event.timestamp(), change));
            value += change;
        }
        previousBid = bid;
        previousAsk = ask;
        long limit = event.timestamp() - windowNanos;
        while (!changes.isEmpty() && changes.peekFirst().timestamp() <= limit) {
            value -= changes.removeFirst().amount();
        }
    }

    /**
     * Returns the order flow imbalance of the window.
     *
     * @return added buying pressure minus added selling pressure, in shares
     */
    public long value() {
        return value;
    }

    /** Buying pressure added on the bid side; 0 if the side was or is empty. */
    private static long bidChange(Optional<PriceLevel> before, Optional<PriceLevel> after) {
        if (before.isEmpty() || after.isEmpty()) {
            return 0;
        }
        long oldPrice = before.get().price();
        long newPrice = after.get().price();
        if (newPrice > oldPrice) {
            return after.get().quantity();
        } else if (newPrice < oldPrice) {
            return -before.get().quantity();
        }
        return after.get().quantity() - before.get().quantity();
    }

    /** Selling pressure added on the ask side; 0 if the side was or is empty. */
    private static long askChange(Optional<PriceLevel> before, Optional<PriceLevel> after) {
        if (before.isEmpty() || after.isEmpty()) {
            return 0;
        }
        long oldPrice = before.get().price();
        long newPrice = after.get().price();
        if (newPrice < oldPrice) {
            return after.get().quantity();
        } else if (newPrice > oldPrice) {
            return -before.get().quantity();
        }
        return after.get().quantity() - before.get().quantity();
    }

    /** One change of pressure in the window. */
    private record Change(long timestamp, long amount) {

    }
}
