package org.example.portfolio;

import org.example.execution.Fill;
import org.example.order.Side;

import java.math.BigDecimal;

public class Portfolio {

    private BigDecimal cash;
    private int position = 0;

    public Portfolio(BigDecimal startingCash) {
        this.cash = startingCash;
    }

    public void apply(Fill fill) {
        BigDecimal amount = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));

        if (fill.side() == Side.BUY) {
            cash = cash.subtract(amount);
            position = position + fill.quantity();
        } else {
            cash = cash.add(amount);
            position = position - fill.quantity();
        }
    }

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