package org.example.research;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.example.backtest.EventBacktester;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.state.SymbolState;
import org.example.research.SignalEvaluationReport.GroupResult;
import org.example.research.SignalEvaluationReport.HorizonResult;
import org.example.signal.Signal;

/**
 * Measures how well signals predict the price, without trading.
 *
 * <p>The events of one symbol are replayed. At every sample time the opinion of every signal and
 * the mid price are written down. Afterwards every sample is compared with the mid price some
 * time later (the horizon), for every group of signals: every signal alone, every pair, and so
 * on, each group mixed with equal weights. This shows which signals know something and whether
 * a signal adds anything to the others.
 */
public class SignalEvaluator {

    /** At most this many signals, because their groups grow fast: 8 signals give 255 groups. */
    public static final int MAX_SIGNALS = 8;

    /** Trade window of the shared market state; the signals keep their own windows. */
    private static final Duration STATE_TRADE_WINDOW = Duration.ofSeconds(10);

    /** Price units (1/10000 dollars) in one cent. */
    private static final double UNITS_PER_CENT = PriceLevel.UNITS_PER_DOLLAR / 100;

    private final long sampleNanos;
    private final List<Duration> horizons;
    private final double convictionThreshold;

    /**
     * Creates the evaluator.
     *
     * @param sampleInterval how often the opinions and the price are written down, for example
     *         1 second
     * @param horizons how far ahead the price is checked, for example 1 second and 1 minute;
     *         each must be a whole number of sample intervals
     * @param convictionThreshold an opinion counts as active only if it is stronger than this,
     *         from 0 (inclusive) to 1 (exclusive), for example 0.3
     * @throws IllegalArgumentException if a setting is not allowed
     */
    public SignalEvaluator(Duration sampleInterval, List<Duration> horizons,
            double convictionThreshold) {
        if (sampleInterval.isNegative() || sampleInterval.isZero()) {
            throw new IllegalArgumentException(
                    "Sample interval must be positive: " + sampleInterval);
        }
        if (horizons.isEmpty()) {
            throw new IllegalArgumentException("Need at least one horizon");
        }
        for (Duration horizon : horizons) {
            if (horizon.isNegative() || horizon.isZero()
                    || horizon.toNanos() % sampleInterval.toNanos() != 0) {
                throw new IllegalArgumentException("Horizon must be a positive whole number of "
                        + "sample intervals: " + horizon);
            }
        }
        if (!(convictionThreshold >= 0 && convictionThreshold < 1)) {
            throw new IllegalArgumentException(
                    "Conviction threshold must be from 0 to below 1: " + convictionThreshold);
        }
        this.sampleNanos = sampleInterval.toNanos();
        this.horizons = horizons.stream().sorted().toList();
        this.convictionThreshold = convictionThreshold;
    }

    /**
     * Replays one period and evaluates the signals.
     *
     * @param store where the events are read from
     * @param symbol the symbol, for example {@code QQQ}
     * @param period events from the warm-up start feed the book and the signals; samples are
     *         taken from the start until the end
     * @param signals new signals of this symbol by name, in the order they should be reported
     * @return the results
     * @throws IllegalArgumentException if there are no signals or more than {@link #MAX_SIGNALS}
     */
    public SignalEvaluationReport evaluate(MarketEventStore store, String symbol,
            TradingPeriod period, Map<String, Signal> signals) {
        if (signals.isEmpty() || signals.size() > MAX_SIGNALS) {
            throw new IllegalArgumentException(
                    "Need 1 to " + MAX_SIGNALS + " signals: " + signals.size());
        }
        long start = EventBacktester.toNanos(period.start());
        long end = EventBacktester.toNanos(period.end());
        Recorder recorder = new Recorder(symbol, signals, start, end);
        store.replay(symbol, EventBacktester.toNanos(period.warmUpStart()), end,
                recorder::onEvent);
        return analyse(symbol, recorder.finish());
    }

    /** Turns the written-down samples into the results. */
    SignalEvaluationReport analyse(String symbol, SignalSamples samples) {
        int signalCount = samples.signals().size();
        List<List<Double>> correlations = new ArrayList<>();
        for (int a = 0; a < signalCount; a++) {
            List<Double> row = new ArrayList<>();
            for (int b = 0; b < signalCount; b++) {
                row.add(correlation(samples.convictions()[a], samples.convictions()[b],
                        samples.size()));
            }
            correlations.add(row);
        }
        List<GroupResult> groups = new ArrayList<>();
        for (List<Integer> members : groups(signalCount)) {
            double[] mixed = mix(samples, members);
            List<HorizonResult> results = new ArrayList<>();
            for (Duration horizon : horizons) {
                results.add(evaluateHorizon(samples, mixed, horizon));
            }
            groups.add(new GroupResult(
                    members.stream().map(samples.signals()::get).toList(), results));
        }
        double[] halfSpreads = Arrays.stream(samples.halfSpreads())
                .filter(value -> !Double.isNaN(value))
                .toArray();
        double averageHalfSpreadCents = halfSpreads.length == 0
                ? Double.NaN
                : Arrays.stream(halfSpreads).average().orElseThrow() / UNITS_PER_CENT;
        return new SignalEvaluationReport(symbol, halfSpreads.length, averageHalfSpreadCents,
                samples.signals(), correlations, groups);
    }

    private HorizonResult evaluateHorizon(SignalSamples samples, double[] mixed,
            Duration horizon) {
        int steps = (int) (horizon.toNanos() / sampleNanos);
        double[] mids = samples.mids();
        double[] opinions = new double[mids.length];
        double[] moves = new double[mids.length];
        int valid = 0;
        int active = 0;
        int moved = 0;
        int hits = 0;
        double directedSum = 0;
        for (int i = 0; i + steps < mids.length; i++) {
            if (Double.isNaN(mids[i]) || Double.isNaN(mids[i + steps])) {
                continue;
            }
            double move = (mids[i + steps] - mids[i]) / UNITS_PER_CENT;
            opinions[valid] = mixed[i];
            moves[valid] = move;
            valid++;
            if (Math.abs(mixed[i]) <= convictionThreshold) {
                continue;
            }
            double directed = Math.signum(mixed[i]) * move;
            active++;
            directedSum += directed;
            if (move != 0) {
                moved++;
                if (directed > 0) {
                    hits++;
                }
            }
        }
        return new HorizonResult(horizon, active,
                moved == 0 ? Double.NaN : (double) hits / moved,
                active == 0 ? Double.NaN : directedSum / active,
                correlation(opinions, moves, valid));
    }

    /** Returns the equal-weight average opinion of the members at every sample. */
    private static double[] mix(SignalSamples samples, List<Integer> members) {
        double[] mixed = new double[samples.size()];
        for (int member : members) {
            double[] convictions = samples.convictions()[member];
            for (int i = 0; i < mixed.length; i++) {
                mixed[i] += convictions[i] / members.size();
            }
        }
        return mixed;
    }

    /**
     * Returns every non-empty group of signals: first every signal alone, then every pair, and
     * so on.
     *
     * @param signalCount the number of signals
     * @return the groups, each as the positions of its signals in increasing order
     */
    static List<List<Integer>> groups(int signalCount) {
        List<List<Integer>> groups = new ArrayList<>();
        for (int size = 1; size <= signalCount; size++) {
            for (int mask = 1; mask < 1 << signalCount; mask++) {
                if (Integer.bitCount(mask) != size) {
                    continue;
                }
                List<Integer> members = new ArrayList<>();
                for (int signal = 0; signal < signalCount; signal++) {
                    if ((mask & 1 << signal) != 0) {
                        members.add(signal);
                    }
                }
                groups.add(members);
            }
        }
        return groups;
    }

    /**
     * Returns the Pearson correlation of the first {@code length} values: +1 if they always move
     * together, -1 if always opposite, 0 if they have nothing to do with each other.
     *
     * @return the correlation, or {@code NaN} if one of them never changes
     */
    static double correlation(double[] x, double[] y, int length) {
        if (length < 2) {
            return Double.NaN;
        }
        double meanX = 0;
        double meanY = 0;
        for (int i = 0; i < length; i++) {
            meanX += x[i] / length;
            meanY += y[i] / length;
        }
        double covariance = 0;
        double varianceX = 0;
        double varianceY = 0;
        for (int i = 0; i < length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        if (varianceX == 0 || varianceY == 0) {
            return Double.NaN;
        }
        return covariance / Math.sqrt(varianceX * varianceY);
    }

    /** Replays the events and writes down the samples. */
    private final class Recorder {

        private final List<String> names;
        private final List<Signal> signals;
        private final SymbolState state;
        private final long start;
        private final double[] mids;
        private final double[] halfSpreads;
        private final double[][] convictions;
        private int next;

        Recorder(String symbol, Map<String, Signal> signals, long start, long end) {
            this.names = List.copyOf(signals.keySet());
            this.signals = List.copyOf(signals.values());
            this.state = new SymbolState(symbol, STATE_TRADE_WINDOW);
            this.start = start;
            int count = (int) ((end - start + sampleNanos - 1) / sampleNanos);
            this.mids = new double[count];
            this.halfSpreads = new double[count];
            this.convictions = new double[signals.size()][count];
        }

        void onEvent(MarketEvent event) {
            // the state before this event is the state at the sample times
            while (next < mids.length && event.timestamp() >= start + next * sampleNanos) {
                record(next++);
            }
            state.apply(event);
            for (Signal signal : signals) {
                signal.update(event, state);
            }
        }

        SignalSamples finish() {
            while (next < mids.length) {
                record(next++);
            }
            return new SignalSamples(names, sampleNanos, mids, halfSpreads, convictions);
        }

        private void record(int sample) {
            OrderBook book = state.book();
            if (book.bestBid().isPresent() && book.bestAsk().isPresent()) {
                long bid = book.bestBid().get().price();
                long ask = book.bestAsk().get().price();
                mids[sample] = (bid + ask) / 2.0;
                halfSpreads[sample] = (ask - bid) / 2.0;
            } else {
                mids[sample] = Double.NaN;
                halfSpreads[sample] = Double.NaN;
            }
            for (int signal = 0; signal < signals.size(); signal++) {
                convictions[signal][sample] = signals.get(signal).conviction();
            }
        }
    }
}
