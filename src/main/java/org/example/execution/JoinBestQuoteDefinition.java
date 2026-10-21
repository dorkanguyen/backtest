package org.example.execution;

import java.util.List;
import java.util.Map;
import org.example.parameter.ParameterSpec;

/** Describes the {@link JoinBestQuote} algorithm and creates it with the chosen settings. */
public class JoinBestQuoteDefinition implements ExecutionAlgoDefinition {

    private static final List<ParameterSpec> PARAMETERS = List.of(
            new ParameterSpec(
                    "toleranceShares",
                    "Changes smaller than this many shares do not replace the waiting order, so "
                            + "it keeps its place in the queue (closing is always allowed).",
                    10, 1, 1_000, true));

    @Override
    public String id() {
        return "joinBestQuote";
    }

    @Override
    public String name() {
        return "Join the best price";
    }

    @Override
    public String description() {
        return "Waits with a limit order at the best bid (to buy) or best ask (to sell) instead "
                + "of paying the spread. Cheaper per trade and earns the maker rebate, but the "
                + "order is only filled when someone trades with it.";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public ExecutionAlgo create(Map<String, Double> values) {
        return new JoinBestQuote(resolve(values).get("toleranceShares").intValue());
    }
}
