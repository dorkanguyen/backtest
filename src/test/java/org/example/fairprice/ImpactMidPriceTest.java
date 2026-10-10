package org.example.fairprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.example.marketdata.BookSide;
import org.example.marketdata.book.OrderBook;
import org.junit.jupiter.api.Test;

/** Tests for {@link ImpactMidPrice} on {@link ExampleBooks#threeLevels()}. */
class ImpactMidPriceTest {

    private final OrderBook book = ExampleBooks.threeLevels();

    @Test
    void smallQuantityGivesTheMidPrice() {
        // 100 shares fit in the best level on both sides: (278.59 + 278.55) / 2
        assertThat(new ImpactMidPrice(100).fairPrice(book).orElseThrow())
                .isCloseTo(278.57, within(1e-9));
    }

    @Test
    void largeQuantityWalksThroughTheLevels() {
        // buy 400: 300 * 278.59 + 100 * 278.60 = 111437.00 -> 278.5925 on average
        // sell 400: 100 * 278.55 + 200 * 278.54 + 100 * 278.53 = 111416.00 -> 278.54 on average
        // fair = (278.5925 + 278.54) / 2 = 278.56625
        assertThat(new ImpactMidPrice(400).fairPrice(book).orElseThrow())
                .isCloseTo(278.56625, within(1e-9));
    }

    @Test
    void notEnoughSharesGivesNoFairPrice() {
        // only 600 shares are offered for sale
        assertThat(new ImpactMidPrice(601).fairPrice(book)).isEmpty();
    }

    @Test
    void manyLevelsAreReadWhenNeeded() {
        OrderBook deep = new OrderBook("AAPL");
        for (int i = 0; i < 20; i++) {
            ExampleBooks.add(deep, i, BookSide.ASK, 1000000 + i * 100, 10);
            ExampleBooks.add(deep, 100 + i, BookSide.BID, 999900 - i * 100, 10);
        }
        // 200 shares take all 20 levels on both sides; the book is symmetric around 99.995
        assertThat(new ImpactMidPrice(200).fairPrice(deep).orElseThrow())
                .isCloseTo(99.995, within(1e-9));
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> new ImpactMidPrice(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
