package org.example.api.backtest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.example.api.backtest.BacktestRequest.CommissionSettings;
import org.example.api.backtest.BacktestRequest.ComponentChoice;
import org.example.api.backtest.BacktestRequest.SignalChoice;
import org.example.backtest.EventBacktestResult;
import org.example.backtest.EventBacktester;
import org.example.backtest.TradingPeriod;
import org.example.catalog.Catalog;
import org.example.execution.BookExecutionEngine;
import org.example.execution.ExecutionAlgo;
import org.example.execution.PerShareCommission;
import org.example.filter.TradingFilter;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.MarketEventStoreFactory;
import org.example.strategy.SignalMixStrategy;
import org.example.strategy.WeightedSignal;

/**
 * Starts backtests in the background and remembers them, so the user interface can ask how they
 * are doing.
 *
 * <p>A request is checked and turned into a strategy before it is queued, so a wrong setting is
 * reported at once and not minutes later. Only the most recent jobs are remembered.
 */
public class BacktestJobService {

    /** When the replay begins: the pre-market opens at 04:00 New York time. */
    static final LocalTime WARM_UP_START = LocalTime.of(4, 0);

    /** How many finished or running jobs are remembered. */
    static final int MAX_JOBS = 50;

    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);
    private static final CommissionSettings DEFAULT_COMMISSION =
            new CommissionSettings(new BigDecimal("0.005"), new BigDecimal("1.00"));

    private final Catalog catalog;
    private final MarketEventStoreFactory stores;
    private final Executor executor;
    private final Map<String, BacktestJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> jobOrder = new ConcurrentLinkedQueue<>();

    /**
     * Creates the service.
     *
     * @param catalog the available signals, filters and execution algorithms
     * @param stores opens the stored market events for every backtest
     * @param executor runs the backtests in the background
     */
    public BacktestJobService(Catalog catalog, MarketEventStoreFactory stores, Executor executor) {
        this.catalog = catalog;
        this.stores = stores;
        this.executor = executor;
    }

    /**
     * Checks a request and starts the backtest in the background.
     *
     * @param request the settings of the backtest
     * @return the new job, queued or already running
     * @throws IllegalArgumentException if a setting is missing, unknown or not allowed
     */
    public BacktestJob submit(BacktestRequest request) {
        Prepared prepared = prepare(request);
        BacktestJob job = new BacktestJob(UUID.randomUUID().toString());
        remember(job);
        executor.execute(() -> run(job, prepared));
        return job;
    }

    /**
     * Finds a job.
     *
     * @param id the identifier of the job
     * @return the job, or empty if there is no such job or it was forgotten
     */
    public Optional<BacktestJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void run(BacktestJob job, Prepared prepared) {
        job.started();
        try (MarketEventStore store = stores.open()) {
            EventBacktestResult result = prepared.backtester().run(store, prepared.symbol(),
                    prepared.period(), prepared.strategy(), prepared.algo(),
                    prepared.startingCash(), job::progressed);
            job.finished(result);
        } catch (RuntimeException e) {
            job.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void remember(BacktestJob job) {
        jobs.put(job.getId(), job);
        jobOrder.add(job.getId());
        while (jobOrder.size() > MAX_JOBS) {
            String oldest = jobOrder.poll();
            if (oldest != null) {
                jobs.remove(oldest);
            }
        }
    }

    private Prepared prepare(BacktestRequest request) {
        String symbol = required(request.symbol(), "symbol");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        final TradingPeriod period = new TradingPeriod(
                required(request.date(), "date").atTime(WARM_UP_START),
                request.date().atTime(required(request.start(), "start")),
                request.date().atTime(required(request.end(), "end")));
        BigDecimal startingCash = required(request.startingCash(), "startingCash");
        if (startingCash.signum() <= 0) {
            throw new IllegalArgumentException("startingCash must be positive");
        }
        final int maxPosition = required(request.maxPosition(), "maxPosition");
        if (request.signals().isEmpty()) {
            throw new IllegalArgumentException("At least one signal is needed");
        }

        List<WeightedSignal> signals = new ArrayList<>();
        for (SignalChoice choice : request.signals()) {
            signals.add(new WeightedSignal(
                    catalog.signal(choice.id()).create(symbol, choice.parameters()),
                    choice.weight()));
        }
        List<TradingFilter> filters = new ArrayList<>();
        for (ComponentChoice choice : request.filters()) {
            filters.add(catalog.filter(choice.id()).create(symbol, choice.parameters()));
        }
        ComponentChoice execution = required(request.execution(), "execution");
        ExecutionAlgo algo =
                catalog.executionAlgo(execution.id()).create(execution.parameters());

        CommissionSettings commission =
                request.commission() == null ? DEFAULT_COMMISSION : request.commission();
        if (required(commission.perShare(), "commission.perShare").signum() < 0
                || required(commission.minimum(), "commission.minimum").signum() < 0) {
            throw new IllegalArgumentException("Commission must not be negative");
        }
        EventBacktester backtester = new EventBacktester(
                new BookExecutionEngine(
                        new PerShareCommission(commission.perShare(), commission.minimum())),
                SAMPLE_INTERVAL);

        return new Prepared(symbol, period, startingCash,
                new SignalMixStrategy(signals, filters, maxPosition), algo, backtester);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /** A checked request, ready to run. */
    private record Prepared(String symbol, TradingPeriod period, BigDecimal startingCash,
            SignalMixStrategy strategy, ExecutionAlgo algo, EventBacktester backtester) {

    }
}
