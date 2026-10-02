package org.example.api;

import org.example.backtest.BacktestResult;
import org.example.backtest.Backtester;
import org.example.marketdata.Candle;
import org.example.marketdata.CandleRepository;
import org.example.strategy.RisingPriceStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.example.execution.CommissionModel;
import org.example.execution.PerShareCommission;

import java.math.BigDecimal;
import java.util.List;

/** REST endpoint that runs a backtest on the candles stored in the database. */
@RestController
public class BacktestController {

    private final CandleRepository candleRepository;

    public BacktestController(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Runs the {@link RisingPriceStrategy} on all stored candles.
     *
     * <p>Uses a starting cash of 1000 and a per-share commission of 0.005 (minimum 1.00).
     * A new strategy is created for every request, because strategies keep state.
     *
     * @return the result with PnL, fills, equity curve and max drawdown
     */
    @GetMapping("/backtest")
    public BacktestResult runBacktest() {
        List<Candle> candles = candleRepository.findAll();
        Backtester backtester = new Backtester();
        CommissionModel commissionModel = new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00"));
        return backtester.run(candles, new RisingPriceStrategy(), new BigDecimal("1000"), commissionModel);
    }
}
