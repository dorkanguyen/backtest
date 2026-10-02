package org.example.backtest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.example.execution.CommissionModel;
import org.example.execution.ExecutionEngine;
import org.example.execution.Fill;
import org.example.marketdata.Candle;
import org.example.order.Order;
import org.example.order.Side;
import org.example.portfolio.Portfolio;
import org.example.strategy.Strategy;

/**
 * Runs a strategy over historical candles and measures the result.
 *
 * <p>For each candle the strategy returns a target position. The difference to the current
 * position becomes an {@link Order}, the {@link ExecutionEngine} fills it, and the
 * {@link Portfolio} is updated. The strategy never trades directly.
 */
public class Backtester {

    /**
     * Runs one backtest.
     *
     * @param candles candles in time order (oldest first)
     * @param strategy a new strategy instance (strategies keep state between candles)
     * @param startingCash cash at the start
     * @param commissionModel how commission is charged on each fill
     * @return the result with PnL, fills, equity curve and max drawdown
     */
    public BacktestResult run(List<Candle> candles, Strategy strategy, BigDecimal startingCash,
            CommissionModel commissionModel) {
        ExecutionEngine executionEngine = new ExecutionEngine(commissionModel);
        Portfolio portfolio = new Portfolio(startingCash);
        List<Fill> fills = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal finalEquity = startingCash;
        BigDecimal peak = startingCash;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (Candle candle : candles) {
            int target = strategy.targetPosition(candle);
            int difference = target - portfolio.getPosition();

            if (difference != 0) {
                Side side = Side.BUY;
                if (difference < 0) {
                    side = Side.SELL;
                }
                Order order = new Order(candle.symbol(), side, Math.abs(difference));
                Fill fill = executionEngine.execute(order, candle);
                portfolio.apply(fill);
                fills.add(fill);
                totalCommission = totalCommission.add(fill.commission());
            }

            finalEquity = portfolio.equity(candle.close());
            equityCurve.add(finalEquity);
            if (finalEquity.compareTo(peak) > 0) {
                peak = finalEquity;
            }
            BigDecimal drawdown = peak.subtract(finalEquity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return new BacktestResult(startingCash, finalEquity, finalEquity.subtract(startingCash),
                fills, equityCurve, maxDrawdown,
                totalCommission);
    }
}
