package org.example.research;

import java.util.List;

/**
 * What a {@link SignalEvaluator} wrote down at every sample time of a replayed day.
 *
 * <p>Sample {@code i} was taken {@code i} sample intervals after the start. A price is
 * {@code NaN} when the book had only one side at that moment.
 *
 * @param signals the names of the signals, in the order of {@code convictions}
 * @param sampleNanos the time between two samples, in nanoseconds
 * @param mids the mid price at every sample, in 1/10000 dollars
 * @param halfSpreads half of the spread at every sample, in 1/10000 dollars
 * @param convictions the opinion of every signal at every sample: {@code [signal][sample]}
 */
record SignalSamples(List<String> signals, long sampleNanos, double[] mids, double[] halfSpreads,
        double[][] convictions) {

    /** Returns the number of samples. */
    int size() {
        return mids.length;
    }
}
