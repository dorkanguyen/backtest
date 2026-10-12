package org.example.signal;

import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;

/**
 * One adviser that watches the market of one symbol and gives its opinion about the price.
 *
 * <p>A signal never trades. It only says how strongly it would buy or sell. Several signals can
 * be mixed with weights, and a separate part of the system turns the result into a position.
 */
public interface Signal {

    /**
     * Updates the signal with one market event.
     *
     * @param event the next event of the symbol
     * @param state the market of the symbol, already updated with {@code event}
     */
    void update(MarketEvent event, SymbolState state);

    /**
     * Returns the current opinion of the signal.
     *
     * @return a number from -1 to +1: +1 means strongly buy, -1 strongly sell, 0 no opinion
     */
    double conviction();
}
