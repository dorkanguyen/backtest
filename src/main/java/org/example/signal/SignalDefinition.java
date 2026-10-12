package org.example.signal;

import java.util.Map;
import org.example.parameter.Definition;

/** Describes one kind of signal and creates signals of that kind with the chosen settings. */
public interface SignalDefinition extends Definition {

    /**
     * Creates a signal for one symbol.
     *
     * @param symbol the symbol the signal will watch, for example {@code AAPL}
     * @param values the chosen settings by key; missing settings get their default value
     * @return a new signal with no history yet
     * @throws IllegalArgumentException if a setting is unknown or not allowed
     */
    Signal create(String symbol, Map<String, Double> values);
}
