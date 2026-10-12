package org.example.signal;

import java.util.List;
import java.util.Map;
import org.example.fairprice.ImpactMidPrice;
import org.example.parameter.ParameterSpec;

/**
 * Describes a {@link FairPriceSignal} that uses the {@link ImpactMidPrice} model, and creates it
 * with the chosen settings.
 */
public class ImpactPriceSignalDefinition implements SignalDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "quantity",
                    "How many shares are bought and sold in the calculation. Larger quantities "
                            + "look deeper into the book; a quantity that fits in the best level "
                            + "gives no opinion.",
                    300, 1, 5_000, true));

    @Override
    public String id() {
        return "impactPrice";
    }

    @Override
    public String name() {
        return "Impact price";
    }

    @Override
    public String description() {
        return "Compares the average price of buying and of selling a given quantity at once. "
                + "Buys when the sellers' side is thin, sells when the buyers' side is.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Signal create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new FairPriceSignal(new ImpactMidPrice(settings.get("quantity").longValue()));
    }
}
