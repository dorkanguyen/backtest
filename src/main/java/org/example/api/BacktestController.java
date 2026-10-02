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

@RestController
public class BacktestController {

    private final CandleRepository candleRepository;

    public BacktestController(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @GetMapping("/backtest")
    public BacktestResult runBacktest() {
        List<Candle> candles = candleRepository.findAll();
        Backtester backtester = new Backtester();
        CommissionModel commissionModel = new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00"));
        return backtester.run(candles, new RisingPriceStrategy(), new BigDecimal("1000"), commissionModel);
    }
}