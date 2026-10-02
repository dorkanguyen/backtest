package org.example.strategy;

import java.math.BigDecimal;
import org.example.marketdata.Candle;

/**
 * Simple momentum strategy: hold 1 share if the close price rose since the previous candle.
 *
 * <p>Holds 0 shares on the first candle and whenever the close did not rise.
 * Only for testing the backtest engine; it is not expected to be profitable.
 */
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
