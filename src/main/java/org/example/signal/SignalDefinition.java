package org.example.signal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes one kind of signal and creates signals of that kind with the chosen settings.
 *
 * <p>The description (name, explanation, parameters) is enough for a user interface to show a
 * settings page, so adding a new kind of signal needs no change anywhere else.
 */
public interface SignalDefinition {

    /** Returns the short identifier used in requests, for example {@code ofi}. */
    String id();

    /** Returns the name shown to the user, for example {@code Order flow imbalance}. */
    String name();

    /** Returns a short explanation of what the signal watches and when it buys or sells. */
    String description();

    /** Returns the settings of the signal, in the order they should be shown. */
    List<ParameterSpec> parameters();

    /**
     * Creates a signal for one symbol.
     *
     * @param symbol the symbol the signal will watch, for example {@code AAPL}
     * @param values the chosen settings by key; missing settings get their default value
     * @return a new signal with no history yet
     * @throws IllegalArgumentException if a setting is unknown or not allowed
     */
    Signal create(String symbol, Map<String, Double> values);

    /**
     * Checks the chosen settings and fills in the missing ones with their default values.
     *
     * @param values the chosen settings by key
     * @return a value for every parameter of this signal
     * @throws IllegalArgumentException if a setting is unknown or not allowed
     */
    default Map<String, Double> resolve(Map<String, Double> values) {
        Map<String, ParameterSpec> specs = new HashMap<>();
        for (ParameterSpec spec : parameters()) {
            specs.put(spec.key(), spec);
        }
        for (String key : values.keySet()) {
            if (!specs.containsKey(key)) {
                throw new IllegalArgumentException(id() + ": unknown parameter " + key);
            }
        }
        Map<String, Double> resolved = new HashMap<>();
        for (ParameterSpec spec : parameters()) {
            double value = values.getOrDefault(spec.key(), spec.defaultValue());
            if (!spec.allows(value)) {
                throw new IllegalArgumentException(
                        id() + ": " + spec.key() + " = " + value + " is not allowed");
            }
            resolved.put(spec.key(), value);
        }
        return resolved;
    }
}
