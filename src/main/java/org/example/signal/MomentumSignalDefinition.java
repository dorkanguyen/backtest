package org.example.signal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link MomentumSignal} and creates it with the chosen settings. */
public class MomentumSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "windowSeconds",
                    "How many seconds back the price move is measured.",
                    60, 1, 600, true),
            new ParameterSpec(
                    "fullMoveCents",
                    "How many cents of move give the strongest opinion.",
                    5, 1, 100, false));

    @Override
    public String id() {
        return "momentum";
    }

    @Override
    public String name() {
        return "Momentum";
    }

    @Override
    public String description() {
        return "Expects the price to keep going the way it went recently. Buys after a rise, "
                + "sells after a fall.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new MomentumSignal(
                Duration.ofSeconds(settings.get("windowSeconds").longValue()),
                settings.get("fullMoveCents") / 100);
    }
}
