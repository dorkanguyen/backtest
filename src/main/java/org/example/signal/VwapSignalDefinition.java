package org.example.signal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link VwapSignal} and creates it with the chosen settings. */
public class VwapSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "windowSeconds",
                    "How many seconds of trades are used for the VWAP.",
                    60, 1, 600, true),
            new ParameterSpec(
                    "fullDistanceCents",
                    "How many cents away from the VWAP the opinion becomes strongest.",
                    5, 1, 100, false));

    @Override
    public String id() {
        return "vwap";
    }

    @Override
    public String name() {
        return "Back to VWAP";
    }

    @Override
    public String description() {
        return "Expects the price to return to the average price of the recent trades (VWAP). "
                + "Sells when the price is above it, buys when it is below.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new VwapSignal(
                symbol,
                Duration.ofSeconds(settings.get("windowSeconds").longValue()),
                settings.get("fullDistanceCents") / 100);
    }
}
