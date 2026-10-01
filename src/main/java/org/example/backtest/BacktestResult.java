package org.example.backtest;

import org.example.execution.Fill;

import java.util.List;

public record BacktestResult(
        double startingCash,
        double finalEquity,
        double pnl,
        List<Fill> fills,
        List<Double> equityCurve
) {
}
