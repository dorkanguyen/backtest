package org.example.signal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Describes the {@link TradeCountFilter} and creates it with the chosen settings. */
public class TradeCountFilterDefinition implements FilterDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "windowSeconds",
                    "How many seconds of trades are counted.",
                    10, 1, 60, true),
            new ParameterSpec(
                    "minTrades",
                    "With fewer trades than this the market counts as quiet: no trading.",
                    5, 0, 1_000, true));

    @Override
    public String id() {
        return "tradeCount";
    }

    @Override
    public String name() {
        return "Quiet market filter";
    }

    @Override
    public String description() {
        return "Stops trading while there are only a few trades, because the signals have little "
                + "information then. The position is kept, nothing is bought or sold.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public TradingFilter create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new TradeCountFilter(
                symbol,
                Duration.ofSeconds(settings.get("windowSeconds").longValue()),
                settings.get("minTrades").intValue());
    }
}
