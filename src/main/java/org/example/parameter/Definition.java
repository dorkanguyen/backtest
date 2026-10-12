package org.example.parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of a description that every configurable component shares: name, explanation and
 * settings, and the checking of the chosen settings.
 *
 * <p>The description is enough for a user interface to show a settings page, so adding a new
 * kind of component needs no change anywhere else.
 */
public interface Definition {

    /** Returns the short identifier used in requests, for example {@code ofi}. */
    String id();

    /** Returns the name shown to the user, for example {@code Order flow imbalance}. */
    String name();

    /** Returns a short explanation of what the component watches and what it does. */
    String description();

    /** Returns the settings of the component, in the order they should be shown. */
    List<ParameterSpec> parameters();

    /**
     * Checks the chosen settings and fills in the missing ones with their default values.
     *
     * @param values the chosen settings by key
     * @return a value for every parameter of this component
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
