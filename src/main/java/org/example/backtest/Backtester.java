package org.example.backtest;

import org.example.execution.ExecutionEngine;
import org.example.execution.Fill;
import org.example.marketdata.Candle;
import org.example.order.Order;
import org.example.order.Side;
import org.example.portfolio.Portfolio;
import org.example.strategy.Strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Backtester {

    public BacktestResult run(List<Candle> candles, Strategy strategy, BigDecimal startingCash) {
        ExecutionEngine executionEngine = new ExecutionEngine();
        Portfolio portfolio = new Portfolio(startingCash);
        List<Fill> fills = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal finalEquity = startingCash;
        BigDecimal peak = startingCash;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

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

        return new BacktestResult(startingCash, finalEquity, finalEquity.subtract(startingCash), fills, equityCurve, maxDrawdown);
    }
}