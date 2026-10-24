package org.example.research;

import java.time.Duration;
import java.util.List;

/**
 * How well some signals predicted the price of one symbol during one replayed period.
 *
 * <p>Prices are compared with the mid price, so the numbers say whether a signal knows the
 * direction of the price, not whether trading on it pays: for that, a move has to be larger
 * than half the spread plus the commission.
 *
 * @param symbol the symbol, for example {@code QQQ}
 * @param sampleCount the number of samples with a two-sided book
 * @param averageHalfSpreadCents the average half spread, in cents: what a market order pays
 *         compared to the mid price
 * @param signals the names of the evaluated signals
 * @param correlations how similar the opinions of two signals were:
 *         {@code correlations.get(a).get(b)} is from -1 to +1, {@code NaN} if a signal never
 *         changed its opinion
 * @param groups the results of every group of signals: first every signal alone, then every
 *         pair, and so on
 */
public record SignalEvaluationReport(String symbol, int sampleCount,
        double averageHalfSpreadCents, List<String> signals, List<List<Double>> correlations,
        List<GroupResult> groups) {

    /**
     * The results of one group of signals, mixed with equal weights.
     *
     * @param signals the names of the signals in the group
     * @param horizons the results for every horizon, from the shortest
     */
    public record GroupResult(List<String> signals, List<HorizonResult> horizons) {
    }

    /**
     * How well a group of signals predicted the price change after one horizon.
     *
     * @param horizon how far ahead the price was checked, for example 1 minute
     * @param activeSamples the number of samples where the opinion was strong enough to act on
     * @param hitRate of the active samples where the price moved, the part where it moved in
     *         the direction of the opinion, from 0 to 1; {@code NaN} if there was none
     * @param averageMoveCents the average price move of the active samples in the direction of
     *         the opinion, in cents: positive means the signal was right on average;
     *         {@code NaN} if there was no active sample
     * @param informationCoefficient the correlation between the opinion and the price move over
     *         all samples, from -1 to +1; {@code NaN} if the opinion never changed
     */
    public record HorizonResult(Duration horizon, int activeSamples, double hitRate,
            double averageMoveCents, double informationCoefficient) {
    }
}
