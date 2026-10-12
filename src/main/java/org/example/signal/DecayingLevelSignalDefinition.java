package org.example.signal;

import java.util.List;
import java.util.Map;
import org.example.fairprice.DecayingLevelPrice;
import org.example.parameter.ParameterSpec;

/**
 * Describes a {@link FairPriceSignal} that uses the {@link DecayingLevelPrice} model, and creates
 * it with the chosen settings.
 */
public class DecayingLevelSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "levels",
                    "How many price levels of the book are used on each side.",
                    3, 1, 10, true),
            new ParameterSpec(
                    "decay",
                    "How much each deeper level counts compared to the previous one. "
                            + "Close to 0: only the best level counts; "
                            + "1: all levels count equally.",
                    0.5, 0.01, 1, false));

    @Override
    public String id() {
        return "decayingLevels";
    }

    @Override
    public String name() {
        return "Decaying levels";
    }

    @Override
    public String description() {
        return "Weighs the waiting buyers and sellers of several price levels, deeper levels "
                + "counting less. Buys when the buyers outweigh the sellers, sells when the "
                + "sellers do.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new FairPriceSignal(new DecayingLevelPrice(
                settings.get("levels").intValue(), settings.get("decay")));
    }
}
