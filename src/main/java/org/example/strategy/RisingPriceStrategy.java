package org.example.strategy;
import org.example.marketdata.Candle;

public class RisingPriceStrategy implements Strategy {

    private double previousClose = 0;

    @Override
    public int targetPosition(Candle candle) {
        int target = 0;

        if (previousClose > 0 && candle.close() > previousClose) {
            target = 1;
        }

        previousClose = candle.close();
        return target;
    }
}