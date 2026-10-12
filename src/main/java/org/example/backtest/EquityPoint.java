package org.example.backtest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The equity at one moment of a backtest: cash plus the value of the position at the mid price.
 *
 * @param time the moment, in New York time
 * @param equity the equity in dollars
 */
public record EquityPoint(LocalDateTime time, BigDecimal equity) {

}
