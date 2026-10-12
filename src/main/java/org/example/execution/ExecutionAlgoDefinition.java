package org.example.execution;

import java.util.Map;
import org.example.parameter.Definition;

/** Describes one kind of execution algorithm and creates algorithms of that kind. */
public interface ExecutionAlgoDefinition extends Definition {

    /**
     * Creates an execution algorithm.
     *
     * @param values the chosen settings by key; missing settings get their default value
     * @return a new algorithm with no history yet
     * @throws IllegalArgumentException if a setting is unknown or not allowed
     */
    ExecutionAlgo create(Map<String, Double> values);
}
