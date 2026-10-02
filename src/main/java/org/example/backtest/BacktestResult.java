package org.example.backtest;

import org.example.execution.Fill;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary of one backtest run.
 *
 * @param startingCash cash at the start
 * @param finalEquity equity after the last candle
 * @param pnl profit and loss: {@code finalEquity - startingCash}
 * @param fills all executed trades, in time order
 * @param equityCurve equity after each candle
 * @param maxDrawdown largest drop of equity from an earlier peak
 * @param totalCommission sum of all commissions paid
 */
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
