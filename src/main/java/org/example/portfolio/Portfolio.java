package org.example.portfolio;

import org.example.execution.Fill;
import org.example.order.Side;

public class Portfolio {

    private double cash;
    private int position = 0;

    public Portfolio(double startingCash) {
        this.cash = startingCash;
    }

    public void apply(Fill fill) {
        double amount = fill.quantity() * fill.price();

        if (fill.side() == Side.BUY) {
            cash = cash - amount;
            position = position + fill.quantity();
        } else {
            cash = cash + amount;
            position = position - fill.quantity();
        }
    }

    public double equity(double currentPrice) {
        return cash + position * currentPrice;
    }

    public double getCash() {
        return cash;
    }

    public int getPosition() {
        return position;
    }
}