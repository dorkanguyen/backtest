package org.example.research;

import java.time.Duration;
import java.util.List;

/**
 * How far the price moved, on average, after a signal gave an opinion of a certain strength:
 * the opinion of the signal translated into cents.
 *
 * <p>Opinions are grouped into bands by their strength, for example 0.5 to 0.7. A selling
 * opinion counts like a buying one of the same strength, with the move turned around, so a
 * positive move always means the signal was right.
 *
 * @param signal the name of the signal
 * @param horizons how far ahead the price was checked, from the shortest
 * @param bands the bands, from the weakest opinion to the strongest
 */
public record CalibrationTable(String signal, List<Duration> horizons, List<Band> bands) {

    /**
     * The opinions of one strength band.
     *
     * @param from the band holds opinions stronger than this, for example 0.5
     * @param to and at most this strong, for example 0.7
     * @param averageHalfSpreadCents the average half spread while the signal was in this band,
     *         in cents: what a market order pays compared to the mid price; {@code NaN} if
     *         the band was empty
     * @param cells the results for every horizon, in the order of the horizons
     */
    public record Band(double from, double to, double averageHalfSpreadCents, List<Cell> cells) {
    }

    /**
     * The result of one band at one horizon.
     *
     * @param samples the number of samples in the band that had a later price
     * @param averageMoveCents the average move in the direction of the opinion, in cents;
     *         {@code NaN} if there was no sample
     */
    public record Cell(int samples, double averageMoveCents) {
    }
}
