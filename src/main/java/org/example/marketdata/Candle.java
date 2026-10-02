package org.example.marketdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One candle (OHLCV bar): the price summary of a symbol over one time period.
 *
 * @param id database id
 * @param symbol instrument, for example {@code AAPL}
 * @param openTime start of the period
 * @param open first price in the period
 * @param high highest price in the period
 * @param low lowest price in the period
 * @param close last price in the period
 * @param volume number of shares traded in the period
 */
public record Candle(
        long id,
        String symbol,
        LocalDateTime openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
