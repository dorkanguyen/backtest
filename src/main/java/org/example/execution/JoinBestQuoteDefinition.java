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
                    10, 1, 1_000, true),
            new ParameterSpec(
                    "guardShares",
                    "The guard compares the impact price of this many shares with the mid price. "
                            + "0 turns the guard off.",
                    300, 0, 5_000, true),
            new ParameterSpec(
                    "guardCents",
                    "If the impact price leans against the waiting order by more than this many "
                            + "cents, the order is cancelled (or not placed): it would probably "
                            + "be filled just before the price moves against it.",
                    0.2, 0, 10, false));

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
        Map<String, Double> resolved = resolve(values);
        return new JoinBestQuote(resolved.get("toleranceShares").intValue(),
                resolved.get("guardShares").intValue(), resolved.get("guardCents"));
    }
}
