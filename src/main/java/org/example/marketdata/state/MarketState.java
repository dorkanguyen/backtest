package org.example.marketdata.state;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import org.example.marketdata.MarketEvent;

/**
 * The state of the whole market we follow: one {@link SymbolState} per symbol and a clock.
 *
 * <p>Events of all symbols arrive in one stream, in time order. Each event goes to the state of
 * its own symbol, so an NVDA event never changes the AAPL book. The clock is the time of the last
 * event, so it works the same for replayed history and for live data.
 */
public class MarketState {

    private final Map<String, SymbolState> states = new TreeMap<>();
    private long currentTime;
    private boolean hasTime;

    /**
     * Creates an empty market state for the given symbols.
     *
     * @param symbols the symbols to follow, for example {@code AAPL} and {@code NVDA}
     * @param tradeWindow how far back the trade flows count trades, for example 10 seconds
     */
    public MarketState(Set<String> symbols, Duration tradeWindow) {
        for (String symbol : symbols) {
            states.put(symbol, new SymbolState(symbol, tradeWindow));
        }
    }

    /**
     * Updates the state of the event's symbol and moves the clock to the event's time.
     *
     * @param event the next event
     * @throws IllegalArgumentException if the symbol is not followed or the event is older than
     *         the previous one (events with the same time are allowed)
     */
    public void apply(MarketEvent event) {
        if (hasTime && event.timestamp() < currentTime) {
            throw new IllegalArgumentException(
                    "Event at " + event.timestamp() + " is older than current time " + currentTime);
        }
        symbol(event.symbol()).apply(event);
        currentTime = event.timestamp();
        hasTime = true;
    }

    /**
     * Returns the state of one symbol.
     *
     * @param symbol the symbol, for example {@code AAPL}
     * @return its order book and trade flow
     * @throws IllegalArgumentException if the symbol is not followed
     */
    public SymbolState symbol(String symbol) {
        SymbolState state = states.get(symbol);
        if (state == null) {
            throw new IllegalArgumentException("Symbol is not followed: " + symbol);
        }
        return state;
    }

    /**
     * Returns the followed symbols in alphabetical order.
     *
     * @return the symbols; the set cannot be changed
     */
    public Set<String> symbols() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * Returns the time of the last event.
     *
     * @return nanoseconds since 1970-01-01 UTC, or empty if no event has arrived yet
     */
    public OptionalLong currentTime() {
        return hasTime ? OptionalLong.of(currentTime) : OptionalLong.empty();
    }
}
