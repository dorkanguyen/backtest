package org.example.signal;

import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;

/**
 * Decides whether trading is allowed right now, for example not in a quiet market.
 *
 * <p>A filter gives no direction. While it does not allow trading, the position is kept as it
 * is: nothing is bought or sold.
 */
public interface TradingFilter {

    /**
     * Updates the filter with one market event.
     *
     * @param event the next event of the symbol
     * @param state the market of the symbol, already updated with {@code event}
     */
    void update(MarketEvent event, SymbolState state);

    /**
     * Tells whether the position may be changed now.
     *
     * @return true if trading is allowed
     */
    boolean allowsTrading();
}
