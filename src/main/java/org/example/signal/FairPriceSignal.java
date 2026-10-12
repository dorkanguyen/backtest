package org.example.signal;

import java.util.Optional;
import java.util.OptionalDouble;
import org.example.fairprice.FairPriceModel;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.state.SymbolState;

/**
 * Buys when a fair price model says the stock is worth more than the mid price, sells when less.
 *
 * <p>The opinion grows with the distance: a fair price halfway between the mid and the best ask
 * gives +0.5, a fair price at the best ask gives +1. Below the mid it is the same with minus
 * signs. If the book has no bid or no ask, there is no opinion.
 */
public class FairPriceSignal implements Signal {

    private final FairPriceModel model;
    private double conviction;

    /**
     * Creates the signal.
     *
     * @param model the fair price model to compare with the mid price
     */
    public FairPriceSignal(FairPriceModel model) {
        this.model = model;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        conviction = calculate(state.book());
    }

    @Override
    public double conviction() {
        return conviction;
    }

    private double calculate(OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        OptionalDouble fair = model.fairPrice(book);
        if (bid.isEmpty() || ask.isEmpty() || fair.isEmpty()) {
            return 0;
        }
        double mid = (bid.get().priceInDollars() + ask.get().priceInDollars()) / 2;
        double halfSpread = (ask.get().priceInDollars() - bid.get().priceInDollars()) / 2;
        if (halfSpread <= 0) {
            return 0;
        }
        double opinion = (fair.getAsDouble() - mid) / halfSpread;
        return Math.max(-1, Math.min(1, opinion));
    }
}
