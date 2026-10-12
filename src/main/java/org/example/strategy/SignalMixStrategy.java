package org.example.strategy;

import java.util.List;
import org.example.filter.TradingFilter;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.signal.Signal;

/**
 * Mixes the opinions of several signals with weights and turns the result into a position.
 *
 * <p>The weights are scaled so that they add up to 1. The target position is the weighted
 * average opinion times the maximum position, rounded to whole shares. While any filter does not
 * allow trading, the current position is kept.
 */
public class SignalMixStrategy implements EventStrategy {

    private final List<Signal> signals;
    private final double[] weights;
    private final List<TradingFilter> filters;
    private final int maxPosition;

    /**
     * Creates the strategy.
     *
     * @param signals the signals and their weights; at least one weight must be more than 0
     * @param filters the filters that may stop trading; may be empty
     * @param maxPosition the largest number of shares held long or short; at least 0
     * @throws IllegalArgumentException if there is no signal, all weights are 0 or the maximum
     *         position is negative
     */
    public SignalMixStrategy(
            List<WeightedSignal> signals, List<TradingFilter> filters, int maxPosition) {
        double totalWeight = 0;
        for (WeightedSignal weighted : signals) {
            totalWeight += weighted.weight();
        }
        if (!(totalWeight > 0)) {
            throw new IllegalArgumentException("At least one signal needs a weight above 0");
        }
        if (maxPosition < 0) {
            throw new IllegalArgumentException(
                    "maxPosition must not be negative, was " + maxPosition);
        }
        this.signals = signals.stream().map(WeightedSignal::signal).toList();
        this.weights = new double[signals.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = signals.get(i).weight() / totalWeight;
        }
        this.filters = List.copyOf(filters);
        this.maxPosition = maxPosition;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        for (Signal signal : signals) {
            signal.update(event, state);
        }
        for (TradingFilter filter : filters) {
            filter.update(event, state);
        }
    }

    /**
     * Returns the weighted average opinion of the signals.
     *
     * @return a number from -1 to +1
     */
    public double conviction() {
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            total += weights[i] * signals.get(i).conviction();
        }
        return total;
    }

    @Override
    public int targetPosition(int currentPosition) {
        for (TradingFilter filter : filters) {
            if (!filter.allowsTrading()) {
                return currentPosition;
            }
        }
        return (int) Math.round(conviction() * maxPosition);
    }
}
