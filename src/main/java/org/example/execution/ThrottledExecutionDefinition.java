package org.example.execution;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link ThrottledExecution} and creates it with the chosen settings. */
public class ThrottledExecutionDefinition implements ExecutionAlgoDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "toleranceShares",
                    "Differences smaller than this many shares are not traded "
                            + "(closing the position is always allowed).",
                    10, 1, 1_000, true),
            new ParameterSpec(
                    "cooldownSeconds",
                    "At least this many seconds must pass between two trades; 0 means no waiting.",
                    30, 0, 3_600, true));

    @Override
    public String id() {
        return "throttled";
    }

    @Override
    public String name() {
        return "Throttled trading";
    }

    @Override
    public String description() {
        return "Trades the whole difference to the target position at once, but ignores small "
                + "differences and waits between trades, so less commission is paid.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public ExecutionAlgo create(Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new ThrottledExecution(
                settings.get("toleranceShares").intValue(),
                Duration.ofSeconds(settings.get("cooldownSeconds").longValue()));
    }
}
