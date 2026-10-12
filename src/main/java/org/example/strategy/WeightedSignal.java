package org.example.strategy;

import org.example.signal.Signal;

/**
 * A signal and how much it counts in a {@link SignalMixStrategy}.
 *
 * @param signal the signal
 * @param weight how much the signal counts compared to the others, at least 0; only the ratio of
 *         the weights matters, so 2, 1, 1 means 50%, 25%, 25%
 */
public record WeightedSignal(Signal signal, double weight) {

    /**
     * Checks the weight.
     *
     * @throws IllegalArgumentException if the weight is negative or not a number
     */
    public WeightedSignal {
        if (!(weight >= 0) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("weight must be 0 or more, was " + weight);
        }
    }
}
