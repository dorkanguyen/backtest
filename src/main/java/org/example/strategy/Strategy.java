package org.example.strategy;

import org.example.marketdata.Candle;

/**
 * Decides which position we want to hold.
 *
 * <p>A strategy never trades directly; it only returns a target position. The
 * {@link org.example.backtest.Backtester} turns the difference into an order. Strategies may keep
 * state (for example the previous candle), so use a new instance for every backtest.
 */
public interface Strategy {

    /**
     * Returns the desired position after seeing a new candle.
     *
     * @param candle the newest candle; candles arrive in time order
     * @return the target number of shares, 0 means no position
     */
    int targetPosition(Candle candle);
}
