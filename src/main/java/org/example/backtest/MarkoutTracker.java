package org.example.backtest;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.execution.Fill;
import org.example.execution.Liquidity;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.order.Side;

/**
 * Collects the markouts of fills during a backtest: for every fill and every horizon it waits
 * until the horizon has passed, then compares the mid price with the fill price.
 *
 * <p>The backtester calls {@link #evaluateUntil} before it applies each event, so the mid is
 * taken from the book as it was at the end of the horizon, like the equity samples.
 */
final class MarkoutTracker {

    private static final double UNITS_PER_CENT = 100;
    private static final double CENTS_PER_DOLLAR = 100;

    private final List<Duration> horizons;
    /** For every horizon, the fills still waiting, in the order of their due time. */
    private final List<ArrayDeque<Waiting>> waiting = new ArrayList<>();
    /** Sum of markout x shares, by horizon and liquidity. */
    private final double[][] sumCentsTimesShares;
    private final long[][] shares;
    private final int[][] fills;

    /**
     * Creates the tracker.
     *
     * @param horizons how long after each fill the mid is taken, for example 1 and 10 seconds
     * @throws IllegalArgumentException if a horizon is not positive
     */
    MarkoutTracker(List<Duration> horizons) {
        for (Duration horizon : horizons) {
            if (horizon.isNegative() || horizon.isZero()) {
                throw new IllegalArgumentException("Horizon must be positive: " + horizon);
            }
            waiting.add(new ArrayDeque<>());
        }
        this.horizons = List.copyOf(horizons);
        int liquidities = Liquidity.values().length;
        this.sumCentsTimesShares = new double[horizons.size()][liquidities];
        this.shares = new long[horizons.size()][liquidities];
        this.fills = new int[horizons.size()][liquidities];
    }

    /**
     * Starts following a fill.
     *
     * @param fill the fill
     * @param timestamp when it happened, in nanoseconds since 1970 UTC; fills must come in time
     *         order
     */
    void add(Fill fill, long timestamp) {
        for (int h = 0; h < horizons.size(); h++) {
            waiting.get(h).add(new Waiting(timestamp + horizons.get(h).toNanos(), fill));
        }
    }

    /**
     * Measures every fill whose horizon ended at or before {@code now}, with the given book.
     *
     * @param now the current time, in nanoseconds since 1970 UTC
     * @param book the book as it was at {@code now}
     */
    void evaluateUntil(long now, OrderBook book) {
        for (int h = 0; h < horizons.size(); h++) {
            ArrayDeque<Waiting> queue = waiting.get(h);
            while (!queue.isEmpty() && queue.peek().due() <= now) {
                Fill fill = queue.poll().fill();
                Optional<Double> mid = midCents(book);
                if (mid.isEmpty()) {
                    continue; // one-sided book: no fair comparison possible
                }
                double priceCents = fill.price().doubleValue() * CENTS_PER_DOLLAR;
                double markout = fill.side() == Side.BUY
                        ? mid.get() - priceCents
                        : priceCents - mid.get();
                int l = fill.liquidity().ordinal();
                sumCentsTimesShares[h][l] += markout * fill.quantity();
                shares[h][l] += fill.quantity();
                fills[h][l]++;
            }
        }
    }

    /**
     * Summarises the measured fills.
     *
     * @return one entry for every horizon and liquidity, horizons in the given order
     */
    List<Markout> summary() {
        List<Markout> result = new ArrayList<>();
        for (int h = 0; h < horizons.size(); h++) {
            for (Liquidity liquidity : Liquidity.values()) {
                int l = liquidity.ordinal();
                double average = shares[h][l] == 0 ? 0 : sumCentsTimesShares[h][l] / shares[h][l];
                result.add(new Markout(horizons.get(h).toMillis(), liquidity, fills[h][l],
                        shares[h][l], average));
            }
        }
        return List.copyOf(result);
    }

    private static Optional<Double> midCents(OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        if (bid.isEmpty() || ask.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((bid.get().price() + ask.get().price()) / 2.0 / UNITS_PER_CENT);
    }

    /** A fill waiting for its horizon to end. */
    private record Waiting(long due, Fill fill) {
    }
}
