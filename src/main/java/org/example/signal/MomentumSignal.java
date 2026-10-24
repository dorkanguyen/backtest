package org.example.signal;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.state.SymbolState;

/**
 * Expects the price to keep going the way it went: buys after the mid price rose during the
 * window, sells after it fell.
 *
 * <p>The opinion grows with the move and reaches -1 or +1 at {@code fullMove} dollars. The
 * signal remembers the mid price every time it changes and forgets what is older than needed.
 * Until a whole window of history is known, or without a bid and an ask, there is no opinion.
 */
public class MomentumSignal implements Signal {

    private final long windowNanos;
    private final double fullMove;
    /** Mid prices newer than the start of the window, oldest first. */
    private final Deque<MidPoint> history = new ArrayDeque<>();
    /** The last mid price from before the window started; null until the window is full. */
    private MidPoint atWindowStart;
    private double conviction;

    /**
     * Creates the signal with no history yet.
     *
     * @param window how far back the move is measured, for example 60 seconds
     * @param fullMove the move in dollars where the opinion is strongest, for example
     *         {@code 0.05}; more than 0
     * @throws IllegalArgumentException if the window or the move is not positive
     */
    public MomentumSignal(Duration window, double fullMove) {
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
        if (!(fullMove > 0)) {
            throw new IllegalArgumentException("fullMove must be positive, was " + fullMove);
        }
        this.windowNanos = window.toNanos();
        this.fullMove = fullMove;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        conviction = calculate(event.timestamp(), state.book());
    }

    @Override
    public double conviction() {
        return conviction;
    }

    private double calculate(long now, OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        if (bid.isEmpty() || ask.isEmpty()) {
            return 0;
        }
        double mid = (bid.get().priceInDollars() + ask.get().priceInDollars()) / 2;
        if (history.isEmpty() || history.peekLast().mid() != mid) {
            history.addLast(new MidPoint(now, mid));
        }

        // the mid price at the start of the window is the last one known at that time
        long windowStart = now - windowNanos;
        while (!history.isEmpty() && history.peekFirst().timestamp() <= windowStart) {
            atWindowStart = history.removeFirst();
        }
        if (atWindowStart == null) {
            return 0;
        }
        double opinion = (mid - atWindowStart.mid()) / fullMove;
        return Math.max(-1, Math.min(1, opinion));
    }

    /** The mid price in dollars from a moment on. */
    private record MidPoint(long timestamp, double mid) {
    }
}
