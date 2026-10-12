package org.example.signal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link OfiSignal} and creates it with the chosen settings. */
public class OfiSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "windowSeconds",
                    "How many seconds of changes at the best prices are added up.",
                    10, 1, 60, true),
            new ParameterSpec(
                    "threshold",
                    "How many shares of imbalance are needed to act; smaller moves are ignored.",
                    500, 0, 10_000, true));

    @Override
    public String id() {
        return "ofi";
    }

    @Override
    public String name() {
        return "Order flow imbalance";
    }

    @Override
    public String description() {
        return "Watches whether buyers or sellers get stronger at the best prices of the book. "
                + "Buys when buyers do, sells when sellers do.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new OfiSignal(
                symbol,
                Duration.ofSeconds(settings.get("windowSeconds").longValue()),
                settings.get("threshold").longValue());
    }
}
