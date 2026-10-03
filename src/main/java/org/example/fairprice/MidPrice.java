package org.example.fairprice;

import java.util.Optional;
import java.util.OptionalDouble;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;

/** Fair price as the middle of the best bid and the best ask. */
public class MidPrice implements FairPriceModel {

    @Override
    public OptionalDouble fairPrice(OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        if (bid.isEmpty() || ask.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((bid.get().priceInDollars() + ask.get().priceInDollars()) / 2);
    }
}
