package org.example.backtest;

import java.time.LocalDateTime;

/**
 * The time span of an event backtest, in New York time.
 *
 * <p>Events from {@code warmUpStart} are replayed so that the order book and the signals are
 * correct, but trading only happens from {@code start} until {@code end}. At {@code end} the
 * position is closed.
 *
 * @param warmUpStart when the replay begins, usually 04:00 when the pre-market opens
 * @param start when trading begins, for example 09:35
 * @param end when trading stops and the position is closed, for example 15:55
 */
public record TradingPeriod(LocalDateTime warmUpStart, LocalDateTime start, LocalDateTime end) {

    /**
     * Checks the order of the times.
     *
     * @throws IllegalArgumentException if the times are not in order or start equals end
     */
    public TradingPeriod {
        if (warmUpStart.isAfter(start) || !start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "Need warmUpStart <= start < end: " + warmUpStart + ", " + start + ", " + end);
        }
    }
}
