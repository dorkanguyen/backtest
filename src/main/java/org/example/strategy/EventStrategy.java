package org.example.strategy;

import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;

/**
 * Decides which position we want to hold, event by event.
 *
 * <p>Like {@link Strategy}, it never trades directly; it only returns a target position. How the
 * difference is traded is decided elsewhere. Strategies keep state, so use a new instance for
 * every backtest.
 */
public interface EventStrategy {

    /**
     * Updates the strategy with one market event.
     *
     * @param event the next event of the symbol
     * @param state the market of the symbol, already updated with {@code event}
     */
    void update(MarketEvent event, SymbolState state);

    /**
     * Returns the desired position now.
     *
     * @param currentPosition the number of shares held now; negative for a short position
     * @return the target number of shares; 0 means no position, negative means short
     */
    int targetPosition(int currentPosition);
}
