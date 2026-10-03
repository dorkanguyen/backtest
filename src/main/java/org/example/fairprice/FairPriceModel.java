package org.example.fairprice;

import java.util.OptionalDouble;
import org.example.marketdata.book.OrderBook;

/**
 * Estimates what a stock is really worth right now, based on its order book.
 *
 * <p>Different models use different parts of the book. The strategy only knows this interface,
 * so the model can be chosen as a parameter.
 */
public interface FairPriceModel {

    /**
     * Calculates the fair price.
     *
     * @param book the current order book
     * @return the fair price in dollars, or empty if the book has no bid or no ask
     */
    OptionalDouble fairPrice(OrderBook book);
}
