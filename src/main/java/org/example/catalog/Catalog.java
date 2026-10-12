package org.example.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.example.execution.ExecutionAlgoDefinition;
import org.example.signal.Definition;
import org.example.signal.FilterDefinition;
import org.example.signal.SignalDefinition;

/**
 * Everything a backtest can be built from: the available kinds of signals, filters and execution
 * algorithms.
 *
 * <p>A user interface lists them with their settings, and a backtest request names them by id.
 *
 * @param signals the kinds of signals
 * @param filters the kinds of trading filters
 * @param executionAlgos the kinds of execution algorithms
 */
public record Catalog(
        List<SignalDefinition> signals,
        List<FilterDefinition> filters,
        List<ExecutionAlgoDefinition> executionAlgos) {

    /**
     * Copies the lists and checks that no id is used twice within a list.
     *
     * @throws IllegalArgumentException if an id appears twice in the same list
     */
    public Catalog {
        signals = List.copyOf(signals);
        filters = List.copyOf(filters);
        executionAlgos = List.copyOf(executionAlgos);
        checkUniqueIds(signals);
        checkUniqueIds(filters);
        checkUniqueIds(executionAlgos);
    }

    /**
     * Finds a kind of signal.
     *
     * @param id the id of the signal, for example {@code ofi}
     * @return the definition
     * @throws IllegalArgumentException if there is no signal with this id
     */
    public SignalDefinition signal(String id) {
        return find(signals, id, "signal");
    }

    /**
     * Finds a kind of trading filter.
     *
     * @param id the id of the filter, for example {@code spread}
     * @return the definition
     * @throws IllegalArgumentException if there is no filter with this id
     */
    public FilterDefinition filter(String id) {
        return find(filters, id, "filter");
    }

    /**
     * Finds a kind of execution algorithm.
     *
     * @param id the id of the algorithm, for example {@code throttled}
     * @return the definition
     * @throws IllegalArgumentException if there is no execution algorithm with this id
     */
    public ExecutionAlgoDefinition executionAlgo(String id) {
        return find(executionAlgos, id, "execution algorithm");
    }

    private static <T extends Definition> T find(List<T> definitions, String id, String kind) {
        for (T definition : definitions) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        throw new IllegalArgumentException("Unknown " + kind + ": " + id);
    }

    private static void checkUniqueIds(List<? extends Definition> definitions) {
        Set<String> ids = new HashSet<>();
        for (Definition definition : definitions) {
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException("Id used twice: " + definition.id());
            }
        }
    }
}
