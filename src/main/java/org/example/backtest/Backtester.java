package org.example.backtest;

import org.example.execution.ExecutionEngine;
import org.example.execution.Fill;
import org.example.marketdata.Candle;
import org.example.order.Order;
import org.example.order.Side;
import org.example.portfolio.Portfolio;
import org.example.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class Backtester {

    public BacktestResult run(List<Candle> candles, Strategy strategy, double startingCash) {
        ExecutionEngine executionEngine = new ExecutionEngine();
        Portfolio portfolio = new Portfolio(startingCash);
        List<Fill> fills = new ArrayList<>();
        List<Double> equityCurve = new ArrayList<>();
        double finalEquity = startingCash;
        double peak = startingCash;
        double maxDrawdown = 0;

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
            if (finalEquity > peak) {
                peak = finalEquity;
            }
            double drawdown = peak - finalEquity;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }


        return new BacktestResult(startingCash, finalEquity, finalEquity - startingCash, fills, equityCurve, maxDrawdown);
    }
}