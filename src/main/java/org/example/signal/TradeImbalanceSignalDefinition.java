package org.example.signal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link TradeImbalanceSignal} and creates it with the chosen settings. */
public class TradeImbalanceSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "windowSeconds",
                    "How many seconds of trades are counted.",
                    10, 1, 300, true),
            new ParameterSpec(
                    "minShares",
                    "With fewer hurried shares than this in the window, there is no opinion.",
                    100, 0, 100_000, true));

    @Override
    public String id() {
        return "tradeImbalance";
    }

    @Override
    public String name() {
        return "Trade pressure";
    }

    @Override
    public String description() {
        return "Compares the shares bought by hurried buyers with the shares sold by hurried "
                + "sellers in the recent trades. Buys when buyers dominate, sells when sellers do.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new TradeImbalanceSignal(
                symbol,
                Duration.ofSeconds(settings.get("windowSeconds").longValue()),
                settings.get("minShares").longValue());
    }
}
