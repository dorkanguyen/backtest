package org.example.research;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.example.backtest.EventBacktester;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.state.SymbolState;
import org.example.signal.Signal;

/**
 * Replays days of one symbol and writes down, at regular sample times, the mid price, half the
 * spread and the opinion of every signal. Research tools such as {@link SignalEvaluator} and
 * {@link SignalCalibrator} work on what it wrote down.
 */
final class SignalSampler {

    /** Trade window of the shared market state; the signals keep their own windows. */
    private static final Duration STATE_TRADE_WINDOW = Duration.ofSeconds(10);

    private final long sampleNanos;
    private final int maxSignals;

    /**
     * Creates the sampler.
     *
     * @param sampleInterval how often the opinions and the price are written down, for example
     *         1 second
     * @param maxSignals the most signals allowed at once
     * @throws IllegalArgumentException if the sample interval is not positive
     */
    SignalSampler(Duration sampleInterval, int maxSignals) {
        if (sampleInterval.isNegative() || sampleInterval.isZero()) {
            throw new IllegalArgumentException(
                    "Sample interval must be positive: " + sampleInterval);
        }
        this.sampleNanos = sampleInterval.toNanos();
        this.maxSignals = maxSignals;
    }

    /**
     * Replays several days, each with new signals, and writes down the samples.
     *
     * @param store where the events are read from
     * @param symbol the symbol, for example {@code QQQ}
     * @param periods one period per day: events from the warm-up start feed the book and the
     *         signals; samples are taken from the start until the end
     * @param newSignals creates new signals of this symbol by name for one day; the same names
     *         every time
     * @return the samples of every day, in the order of the periods
     * @throws IllegalArgumentException if there is no period, there are no signals or too many,
     *         or the days get different signals
     */
    List<SignalSamples> sample(MarketEventStore store, String symbol,
            List<TradingPeriod> periods, Supplier<Map<String, Signal>> newSignals) {
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("Need at least one period");
        }
        List<SignalSamples> samples = new ArrayList<>();
        for (TradingPeriod period : periods) {
            Map<String, Signal> signals = newSignals.get();
            if (signals.isEmpty() || signals.size() > maxSignals) {
                throw new IllegalArgumentException(
                        "Need 1 to " + maxSignals + " signals: " + signals.size());
            }
            if (!samples.isEmpty()
                    && !samples.get(0).signals().equals(List.copyOf(signals.keySet()))) {
                throw new IllegalArgumentException("Every day needs the same signals: "
                        + samples.get(0).signals() + " and " + signals.keySet());
            }
            long start = EventBacktester.toNanos(period.start());
            long end = EventBacktester.toNanos(period.end());
            Recorder recorder = new Recorder(symbol, signals, start, end);
            store.replay(symbol, EventBacktester.toNanos(period.warmUpStart()), end,
                    recorder::onEvent);
            samples.add(recorder.finish());
        }
        return samples;
    }

    /** Replays the events of one day and writes down the samples. */
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
