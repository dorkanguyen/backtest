package org.example.marketdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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