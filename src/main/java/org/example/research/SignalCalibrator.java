package org.example.research;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.example.marketdata.book.PriceLevel;
import org.example.research.CalibrationTable.Band;
import org.example.research.CalibrationTable.Cell;

/**
 * Translates the opinion of a signal into the price move it can expect, in cents.
 *
 * <p>The opinions are sorted into strength bands, and for every band and horizon the average
 * later move of the mid price is measured. A table made on some days should be checked on other
 * days: on the days it was made from it always looks better than it is.
 */
public class SignalCalibrator {

    /** Price units (1/10000 dollars) in one cent. */
    private static final double UNITS_PER_CENT = PriceLevel.UNITS_PER_DOLLAR / 100;

    private final List<Duration> horizons;
    private final List<Double> bandEdges;

    /**
     * Creates the calibrator.
     *
     * @param horizons how far ahead the price is checked, for example 1 second and 1 minute
     * @param bandEdges the borders of the strength bands, increasing, from 0 to 1: for example
     *         0.3, 0.5, 1 makes the bands 0.3 to 0.5 and 0.5 to 1; weaker opinions are left out
     * @throws IllegalArgumentException if there is no horizon, a horizon is not positive, or
     *         the band edges are not at least two increasing numbers from 0 to 1
     */
    public SignalCalibrator(List<Duration> horizons, List<Double> bandEdges) {
        if (horizons.isEmpty()) {
            throw new IllegalArgumentException("Need at least one horizon");
        }
        for (Duration horizon : horizons) {
            if (horizon.isNegative() || horizon.isZero()) {
                throw new IllegalArgumentException("Horizon must be positive: " + horizon);
            }
        }
        if (bandEdges.size() < 2 || bandEdges.get(0) < 0
                || bandEdges.get(bandEdges.size() - 1) > 1) {
            throw new IllegalArgumentException(
                    "Need at least two band edges from 0 to 1: " + bandEdges);
        }
        for (int i = 1; i < bandEdges.size(); i++) {
            if (bandEdges.get(i) <= bandEdges.get(i - 1)) {
                throw new IllegalArgumentException("Band edges must increase: " + bandEdges);
            }
        }
        this.horizons = horizons.stream().sorted().toList();
        this.bandEdges = List.copyOf(bandEdges);
    }

    /**
     * Makes the table of one signal from the samples of some days.
     *
     * @param days the samples of every day; a move never reaches from one day into the next
     * @param signal the name of the signal, one of the names in the samples
     * @return the table
     * @throws IllegalArgumentException if there are no days, the signal is unknown, or a
     *         horizon is not a whole number of sample intervals
     */
    CalibrationTable calibrate(List<SignalSamples> days, String signal) {
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Need at least one day");
        }
        int index = days.get(0).signals().indexOf(signal);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown signal: " + signal);
        }
        int bandCount = bandEdges.size() - 1;
        double[] halfSpreadSums = new double[bandCount];
        int[] halfSpreadCounts = new int[bandCount];
        double[][] moveSums = new double[bandCount][horizons.size()];
        int[][] moveCounts = new int[bandCount][horizons.size()];
        for (SignalSamples day : days) {
            double[] mids = day.mids();
            double[] convictions = day.convictions()[index];
            int[] steps = steps(day.sampleNanos());
            for (int i = 0; i < mids.length; i++) {
                int band = band(Math.abs(convictions[i]));
                if (band < 0 || Double.isNaN(mids[i])) {
                    continue;
                }
                halfSpreadSums[band] += day.halfSpreads()[i];
                halfSpreadCounts[band]++;
                double direction = Math.signum(convictions[i]);
                for (int h = 0; h < steps.length; h++) {
                    int later = i + steps[h];
                    // the later price must be on the same day and from a two-sided book
                    if (later < mids.length && !Double.isNaN(mids[later])) {
                        moveSums[band][h] += direction * (mids[later] - mids[i]) / UNITS_PER_CENT;
                        moveCounts[band][h]++;
                    }
                }
            }
        }

        List<Band> bands = new ArrayList<>();
        for (int band = 0; band < bandCount; band++) {
            List<Cell> cells = new ArrayList<>();
            for (int h = 0; h < horizons.size(); h++) {
                int count = moveCounts[band][h];
                cells.add(new Cell(count, count == 0 ? Double.NaN : moveSums[band][h] / count));
            }
            double halfSpread = halfSpreadCounts[band] == 0 ? Double.NaN
                    : halfSpreadSums[band] / halfSpreadCounts[band] / UNITS_PER_CENT;
            bands.add(new Band(bandEdges.get(band), bandEdges.get(band + 1), halfSpread, cells));
        }
        return new CalibrationTable(signal, horizons, bands);
    }

    /** Returns every horizon as a number of samples. */
    private int[] steps(long sampleNanos) {
        int[] steps = new int[horizons.size()];
        for (int h = 0; h < steps.length; h++) {
            long nanos = horizons.get(h).toNanos();
            if (nanos % sampleNanos != 0) {
                throw new IllegalArgumentException("Horizon must be a whole number of sample "
                        + "intervals: " + horizons.get(h));
            }
            steps[h] = (int) (nanos / sampleNanos);
        }
        return steps;
    }

    /** Returns the band of an opinion strength, or -1 if it is in none of them. */
    private int band(double strength) {
        for (int band = 0; band < bandEdges.size() - 1; band++) {
            if (strength > bandEdges.get(band) && strength <= bandEdges.get(band + 1)) {
                return band;
            }
        }
        return -1;
    }
}
