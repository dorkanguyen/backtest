package org.example.marketdata;

import java.time.LocalDateTime;

public record Candle(
        long id,
        String symbol,
        LocalDateTime openTime,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
}