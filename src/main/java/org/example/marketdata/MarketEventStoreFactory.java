package org.example.marketdata;

/**
 * Opens a connection to the stored market events. Every backtest opens its own and closes it when
 * it is done, so several backtests can read at the same time.
 */
@FunctionalInterface
public interface MarketEventStoreFactory {

    /**
     * Opens the store.
     *
     * @return an open store; the caller must close it
     */
    MarketEventStore open();
}
