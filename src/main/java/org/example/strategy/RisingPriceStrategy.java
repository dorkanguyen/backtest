package org.example.strategy;
import org.example.marketdata.Candle;

import java.math.BigDecimal;

public class RisingPriceStrategy implements Strategy {

    private BigDecimal previousClose = null;

    @Override
    public int targetPosition(Candle candle) {
        int target = 0;

        if (previousClose != null && candle.close().compareTo(previousClose) > 0) {
            target = 1;
        }

        previousClose = candle.close();
        return target;
    }
}