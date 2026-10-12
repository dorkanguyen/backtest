package org.example.signal;

import java.util.List;
import java.util.Map;
import org.example.marketdata.book.PriceLevel;

/** Describes the {@link SpreadFilter} and creates it with the chosen settings. */
public class SpreadFilterDefinition implements FilterDefinition {

    private static final long UNITS_PER_CENT = Math.round(PriceLevel.UNITS_PER_DOLLAR / 100);

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "maxSpreadCents",
                    "Trading is allowed only while the spread is at most this many cents.",
                    2, 1, 100, true));

    @Override
    public String id() {
        return "spread";
    }

    @Override
    public String name() {
        return "Wide spread filter";
    }

    @Override
    public String description() {
        return "Stops trading while the spread is wide, because every trade costs more then. "
                + "The position is kept, nothing is bought or sold.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public TradingFilter create(String symbol, Map<String, Double> values) {
        Map<String, Double> settings = resolve(values);
        return new SpreadFilter(settings.get("maxSpreadCents").longValue() * UNITS_PER_CENT);
    }
}
