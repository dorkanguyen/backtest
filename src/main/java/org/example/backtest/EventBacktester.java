package org.example.backtest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import org.example.execution.CommissionModel;
import org.example.execution.ExecutionAlgo;
import org.example.execution.ExecutionSimulator;
import org.example.execution.Fill;
import org.example.execution.Instruction;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.state.SymbolState;
import org.example.order.Order;
import org.example.order.Side;
import org.example.portfolio.Portfolio;
import org.example.strategy.EventStrategy;

/**
 * Replays the stored events of one symbol and trades them with a strategy, like it would happen
 * live.
 *
 * <p>For every event: the market state is updated, waiting limit orders move forward in their
 * queues and may be filled, the strategy is updated and says its target position, and the
 * execution algorithm decides what to do; its instructions are carried out by an
 * {@link ExecutionSimulator}. Every fill is reported back to the algorithm. Before trading
 * starts, events only warm up the book and the signals. At the end the waiting orders are
 * cancelled and the position is closed with a market order. The equity is sampled at a fixed
 * interval, because recording it at millions of events would be far too much.
 */
public class EventBacktester {

    /** Exchange time zone: trading periods and fill times are in New York time. */
    public static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

    /** Trade window of the shared market state; the signals keep their own windows. */
    private static final Duration STATE_TRADE_WINDOW = Duration.ofSeconds(10);

    /**
     * Id of the order that closes the position at the end. Execution algorithms number their
     * orders from 1.
     */
    static final long CLOSING_ORDER_ID = 0;

    /** Progress is reported at most once per this much replayed market time. */
    private static final long PROGRESS_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final CommissionModel commissionModel;
    private final long sampleNanos;

    /**
     * Creates the backtester.
     *
     * @param commissionModel calculates the commission of every fill
     * @param sampleInterval how often the equity is recorded, for example 1 second
     * @throws IllegalArgumentException if the sample interval is not positive
     */
    public EventBacktester(CommissionModel commissionModel, Duration sampleInterval) {
        if (sampleInterval.isNegative() || sampleInterval.isZero()) {
            throw new IllegalArgumentException(
                    "Sample interval must be positive: " + sampleInterval);
        }
        this.commissionModel = commissionModel;
        this.sampleNanos = sampleInterval.toNanos();
    }

    /**
     * Runs one backtest.
     *
     * @param store where the events are read from
     * @param symbol the symbol to trade, for example {@code AAPL}
     * @param period when to warm up, trade and close the position
     * @param strategy a new strategy, not used before
     * @param algo a new execution algorithm, not used before
     * @param startingCash the cash at the start
     * @return the outcome
     */
    public EventBacktestResult run(MarketEventStore store, String symbol, TradingPeriod period,
            EventStrategy strategy, ExecutionAlgo algo, BigDecimal startingCash) {
        return run(store, symbol, period, strategy, algo, startingCash, progress -> {});
    }

    /**
     * Runs one backtest and reports how far it got.
     *
     * @param store where the events are read from
     * @param symbol the symbol to trade, for example {@code AAPL}
     * @param period when to warm up, trade and close the position
     * @param strategy a new strategy, not used before
     * @param algo a new execution algorithm, not used before
     * @param startingCash the cash at the start
     * @param progress receives the finished part of the period, from 0 to 1, about once per
     *         replayed minute and 1 at the end
     * @return the outcome
     */
    public EventBacktestResult run(MarketEventStore store, String symbol, TradingPeriod period,
            EventStrategy strategy, ExecutionAlgo algo, BigDecimal startingCash,
            DoubleConsumer progress) {
        long from = toNanos(period.warmUpStart());
        long to = toNanos(period.end());
        Run run = new Run(symbol, toNanos(period.start()), strategy, algo, startingCash);
        long[] nextReport = {from};
        store.replay(symbol, from, to, event -> {
            if (event.timestamp() >= nextReport[0]) {
                progress.accept((double) (event.timestamp() - from) / (to - from));
                nextReport[0] = event.timestamp() + PROGRESS_INTERVAL_NANOS;
            }
            run.onEvent(event);
        });
        EventBacktestResult result = run.finish(to);
        progress.accept(1);
        return result;
    }

    /** Converts a New York time to nanoseconds since 1970 UTC. */
    static long toNanos(LocalDateTime time) {
        Instant instant = time.atZone(EXCHANGE_ZONE).toInstant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    /** Converts nanoseconds since 1970 UTC to New York time. */
    static LocalDateTime toTime(long nanos) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(0, nanos), EXCHANGE_ZONE);
    }

    /** The state of one backtest run. */
    private final class Run {

        private final String symbol;
        private final long start;
        private final EventStrategy strategy;
        private final ExecutionAlgo algo;
        private final BigDecimal startingCash;
        private final SymbolState state;
        private final Portfolio portfolio;
        private final ExecutionSimulator execution;
        private final List<Fill> fills = new ArrayList<>();
        private final List<EquityPoint> equityCurve = new ArrayList<>();
        private long nextSample;

        Run(String symbol, long start, EventStrategy strategy, ExecutionAlgo algo,
                BigDecimal startingCash) {
            this.symbol = symbol;
            this.start = start;
            this.strategy = strategy;
            this.algo = algo;
            this.startingCash = startingCash;
            this.state = new SymbolState(symbol, STATE_TRADE_WINDOW);
            this.portfolio = new Portfolio(startingCash);
            this.execution = new ExecutionSimulator(commissionModel);
            this.nextSample = start;
        }

        void onEvent(MarketEvent event) {
            // the book before this event is the book at the sample times
            while (event.timestamp() >= nextSample) {
                sample(nextSample);
                nextSample += sampleNanos;
            }
            LocalDateTime time = toTime(event.timestamp());
            state.apply(event);
            report(execution.onEvent(event, time));
            strategy.update(event, state);
            if (event.timestamp() < start) {
                return;
            }
            int position = portfolio.getPosition();
            List<Instruction> instructions = algo.decide(event.timestamp(), symbol, position,
                    strategy.targetPosition(position), state.book());
            for (Instruction instruction : instructions) {
                report(execution.carryOut(instruction, state.book(), time));
            }
        }

        EventBacktestResult finish(long end) {
            while (end > nextSample) {
                sample(nextSample);
                nextSample += sampleNanos;
            }
            execution.cancelAll();
            int position = portfolio.getPosition();
            if (position != 0) {
                Side side = position > 0 ? Side.SELL : Side.BUY;
                Order closing = Order.market(CLOSING_ORDER_ID, symbol, side, Math.abs(position));
                // not reported to the algorithm: the closing order is not one of its orders
                execution.carryOut(new Instruction.Place(closing), state.book(), toTime(end))
                        .forEach(this::book);
            }
            sample(end);

            BigDecimal finalEquity = equityCurve.isEmpty()
                    ? startingCash
                    : equityCurve.get(equityCurve.size() - 1).equity();
            BigDecimal totalCommission = BigDecimal.ZERO;
            for (Fill fill : fills) {
                totalCommission = totalCommission.add(fill.commission());
            }
            return new EventBacktestResult(symbol, startingCash, finalEquity,
                    finalEquity.subtract(startingCash), totalCommission, maxDrawdown(),
                    List.copyOf(fills), List.copyOf(equityCurve));
        }

        /** Books the fills and tells the algorithm about them. */
        private void report(List<Fill> newFills) {
            for (Fill fill : newFills) {
                book(fill);
                algo.onFill(fill);
            }
        }

        private void book(Fill fill) {
            portfolio.apply(fill);
            fills.add(fill);
        }

        private void sample(long timestamp) {
            midPrice(state.book()).ifPresent(mid -> equityCurve.add(
                    new EquityPoint(toTime(timestamp), portfolio.equity(mid))));
        }

        private BigDecimal maxDrawdown() {
            BigDecimal peak = startingCash;
            BigDecimal maxDrawdown = BigDecimal.ZERO;
            for (EquityPoint point : equityCurve) {
                if (point.equity().compareTo(peak) > 0) {
                    peak = point.equity();
                }
                BigDecimal drawdown = peak.subtract(point.equity());
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
            return maxDrawdown;
        }
    }

    private static Optional<BigDecimal> midPrice(OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        if (bid.isEmpty() || ask.isEmpty()) {
            return Optional.empty();
        }
        // (bid + ask) / 2 in dollars: the sum in 1/10000 dollars, with 5 decimal places
        return Optional.of(BigDecimal.valueOf(bid.get().price() + ask.get().price(), 4)
                .divide(BigDecimal.valueOf(2)));
    }
}
