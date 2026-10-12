package org.example.signal;

import java.util.Map;

/** Describes one kind of trading filter and creates filters of that kind. */
public interface FilterDefinition extends Definition {

    /**
     * Creates a filter for one symbol.
     *
     * @param symbol the symbol the filter will watch, for example {@code AAPL}
     * @param values the chosen settings by key; missing settings get their default value
     * @return a new filter with no history yet
     * @throws IllegalArgumentException if a setting is unknown or not allowed
     */
    TradingFilter create(String symbol, Map<String, Double> values);
}
