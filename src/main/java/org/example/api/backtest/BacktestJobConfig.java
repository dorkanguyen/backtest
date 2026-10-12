package org.example.api.backtest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.example.catalog.Catalog;
import org.example.marketdata.MarketEventStoreFactory;
import org.example.marketdata.SqliteMarketEventStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the parts needed to run backtests in the background. */
@Configuration
public class BacktestJobConfig {

    /** How many backtests may run at the same time; each one needs a lot of memory and CPU. */
    private static final int WORKERS = 2;

    /**
     * Opens the market event database for every backtest.
     *
     * @param databaseFile the path of the database, from the {@code marketdata.db-path} setting
     * @return the factory
     */
    @Bean
    public MarketEventStoreFactory marketEventStoreFactory(
            @Value("${marketdata.db-path}") String databaseFile) {
        return () -> new SqliteMarketEventStore(databaseFile);
    }

    /**
     * Creates the threads that run the backtests. Spring shuts them down when the application
     * stops.
     *
     * @return the worker threads
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService backtestWorkers() {
        return Executors.newFixedThreadPool(WORKERS);
    }

    /**
     * Creates the service that starts and remembers backtests.
     *
     * @param catalog the available signals, filters and execution algorithms
     * @param stores opens the market event database
     * @param backtestWorkers the threads that run the backtests
     * @return the service
     */
    @Bean
    public BacktestJobService backtestJobService(Catalog catalog, MarketEventStoreFactory stores,
            ExecutorService backtestWorkers) {
        return new BacktestJobService(catalog, stores, backtestWorkers);
    }
}
