package org.example.portfolio;

import java.math.BigDecimal;
import org.example.execution.Fill;
import org.example.order.Side;

/**
 * Tracks cash and position, and calculates equity.
 *
 * <p>Equity = cash + position * current price.
 */
public class Portfolio {

    private BigDecimal cash;
    private int position = 0;

    /** Creates a portfolio with the given cash and no position. */
    public Portfolio(BigDecimal startingCash) {
        this.cash = startingCash;
    }

    /**
     * Updates cash and position after a fill. The commission is always paid from cash.
     *
     * @param fill the executed trade
     */
    public void apply(Fill fill) {
        BigDecimal amount = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));

        if (fill.side() == Side.BUY) {
            cash = cash.subtract(amount).subtract(fill.commission());
            position = position + fill.quantity();
        } else {
            cash = cash.add(amount).subtract(fill.commission());
            position = position - fill.quantity();
        }
    }

    /**
     * Returns the total value of the portfolio.
     *
     * @param currentPrice price used to value the position
     * @return cash plus the value of the position
     */
    public BigDecimal equity(BigDecimal currentPrice) {
        return cash.add(currentPrice.multiply(BigDecimal.valueOf(position)));
    }

    public BigDecimal getCash() {
        return cash;
    }

    public int getPosition() {
        return position;
    }
}
