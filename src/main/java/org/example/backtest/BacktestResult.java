package org.example.backtest;

import org.example.execution.Fill;

import java.math.BigDecimal;
import java.util.List;

public record BacktestResult(
        BigDecimal startingCash,
        BigDecimal finalEquity,
        BigDecimal pnl,
        List<Fill> fills,
        List<BigDecimal> equityCurve,
        BigDecimal maxDrawdown,
        BigDecimal totalCommission
) {
}
