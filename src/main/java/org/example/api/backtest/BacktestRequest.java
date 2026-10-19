package org.example.api.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * The settings of one backtest, as sent by the user interface in {@code POST /api/backtests}.
 *
 * <p>Missing lists and parameter maps count as empty; missing parameters get their default value.
 *
 * @param symbol the symbol to trade, for example {@code AAPL}
 * @param date the trading day
 * @param start when trading begins, New York time, for example 09:35
 * @param end when trading stops and the position is closed, for example 15:55
 * @param startingCash the cash at the start, in dollars
 * @param maxPosition the largest number of shares held long or short
 * @param signals the signals to mix, with their weights and settings
 * @param filters the trading filters with their settings
 * @param execution the execution algorithm with its settings
 * @param commission the commission of every fill
 */
public record BacktestRequest(
        String symbol,
        LocalDate date,
        LocalTime start,
        LocalTime end,
        BigDecimal startingCash,
        Integer maxPosition,
        List<SignalChoice> signals,
        List<ComponentChoice> filters,
        ComponentChoice execution,
        CommissionSettings commission) {

    /** Replaces missing lists with empty ones. */
    public BacktestRequest {
        signals = signals == null ? List.of() : List.copyOf(signals);
        filters = filters == null ? List.of() : List.copyOf(filters);
    }

    /**
     * One chosen signal.
     *
     * @param id the id of the signal in the catalog, for example {@code vwap}
     * @param weight how much the signal counts compared to the others
     * @param parameters the chosen settings by key
     */
    public record SignalChoice(String id, double weight, Map<String, Double> parameters) {

        /** Replaces a missing parameter map with an empty one. */
        public SignalChoice {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /**
     * One chosen filter or execution algorithm.
     *
     * @param id the id in the catalog, for example {@code spread}
     * @param parameters the chosen settings by key
     */
    public record ComponentChoice(String id, Map<String, Double> parameters) {

        /** Replaces a missing parameter map with an empty one. */
        public ComponentChoice {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /**
     * The commission of every fill: a broker fee per share with a minimum, plus exchange and
     * clearing fees per share.
     *
     * <p>Missing exchange and clearing fees count as zero, so {@code perShare 0.005, minimum 1.00}
     * alone is a fixed per-share price.
     *
     * @param perShare broker fee in dollars per share, for example 0.0035
     * @param minimum the least broker fee of one fill, for example 0.35
     * @param exchangePerShare exchange fee per share, for example 0.003
     * @param clearingPerShare clearing fee per share, for example 0.0002
     */
    public record CommissionSettings(BigDecimal perShare, BigDecimal minimum,
            BigDecimal exchangePerShare, BigDecimal clearingPerShare) {

        /** Replaces missing exchange and clearing fees with zero. */
        public CommissionSettings {
            exchangePerShare = exchangePerShare == null ? BigDecimal.ZERO : exchangePerShare;
            clearingPerShare = clearingPerShare == null ? BigDecimal.ZERO : clearingPerShare;
        }
    }
}
