package org.example.backtest;

import java.math.BigDecimal;
import java.util.List;
import org.example.execution.Fill;

/**
 * The outcome of an event backtest.
 *
 * @param symbol the traded symbol
 * @param startingCash the cash at the start
 * @param finalEquity the cash at the end, after the position was closed
 * @param pnl profit or loss: final equity minus starting cash, commission included
 * @param totalCommission the commission paid for all fills
 * @param maxDrawdown the largest fall of the equity from an earlier peak
 * @param fills every fill, in time order
 * @param equityCurve the equity sampled at a fixed interval, and at the end
 */
public record EventBacktestResult(
        String symbol,
        BigDecimal startingCash,
        BigDecimal finalEquity,
        BigDecimal pnl,
        BigDecimal totalCommission,
        BigDecimal maxDrawdown,
        List<Fill> fills,
        List<EquityPoint> equityCurve) {

}
