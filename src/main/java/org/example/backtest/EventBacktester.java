package org.example.backtest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.execution.BookExecutionEngine;
import org.example.execution.ExecutionAlgo;
import org.example.execution.Fill;
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
 * <p>For every event: the market state and the strategy are updated, the strategy says its
 * target position, the execution algorithm decides whether to send an order, and the order is
 * filled from the book. Before trading starts, events only warm up the book and the signals. At
 * the end the position is closed. The equity is sampled at a fixed interval, because recording it
 * at millions of events would be far too much.
 */
public class EventBacktester {

    /** Exchange time zone: trading periods and fill times are in New York time. */
    public static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

    /** Trade window of the shared market state; the signals keep their own windows. */
    private static final Duration STATE_TRADE_WINDOW = Duration.ofSeconds(10);

    private final BookExecutionEngine engine;
    private final long sampleNanos;

    /**
     * Creates the backtester.
     *
     * @param engine fills the orders from the book
     * @param sampleInterval how often the equity is recorded, for example 1 second
     * @throws IllegalArgumentException if the sample interval is not positive
     */
    public EventBacktester(BookExecutionEngine engine, Duration sampleInterval) {
        if (sampleInterval.isNegative() || sampleInterval.isZero()) {
            throw new IllegalArgumentException(
                    "Sample interval must be positive: " + sampleInterval);
        }
        this.engine = engine;
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
        Run run = new Run(symbol, toNanos(period.start()), strategy, algo, startingCash);
        store.replay(symbol, toNanos(period.warmUpStart()), toNanos(period.end()), run::onEvent);
        return run.finish(toNanos(period.end()));
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
            this.nextSample = start;
        }

        void onEvent(MarketEvent event) {
            // the book before this event is the book at the sample times
            while (event.timestamp() >= nextSample) {
                sample(nextSample);
                nextSample += sampleNanos;
            }
            state.apply(event);
            strategy.update(event, state);
            if (event.timestamp() < start) {
                return;
            }
            int position = portfolio.getPosition();
            algo.decide(event.timestamp(), symbol, position, strategy.targetPosition(position))
                    .ifPresent(order -> fill(order, event.timestamp()));
        }

        EventBacktestResult finish(long end) {
            while (end > nextSample) {
                sample(nextSample);
                nextSample += sampleNanos;
            }
            int position = portfolio.getPosition();
            if (position != 0) {
                Side side = position > 0 ? Side.SELL : Side.BUY;
                fill(new Order(symbol, side, Math.abs(position)), end);
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

        private void fill(Order order, long timestamp) {
            Optional<Fill> fill = engine.execute(order, state.book(), toTime(timestamp));
            fill.ifPresent(f -> {
                portfolio.apply(f);
                fills.add(f);
            });
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
