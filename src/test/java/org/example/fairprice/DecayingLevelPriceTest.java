package org.example.fairprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.example.marketdata.BookSide;
import org.example.marketdata.book.OrderBook;
import org.junit.jupiter.api.Test;

/** Tests for {@link DecayingLevelPrice} on {@link ExampleBooks#threeLevels()}. */
class DecayingLevelPriceTest {

    private final OrderBook book = ExampleBooks.threeLevels();

    @Test
    void decayOfOneIsTheSameAsMultiLevel() {
        double multiLevel = new MultiLevelPrice(3).fairPrice(book).orElseThrow();

        assertThat(new DecayingLevelPrice(3, 1.0).fairPrice(book).orElseThrow())
                .isCloseTo(multiLevel, within(1e-9));
    }

    @Test
    void deeperLevelsCountLess() {
        // bid: 100 + 0.5 * 200 + 0.25 * 900 = 425, ask: 300 + 0.5 * 200 + 0.25 * 100 = 425
        // equal weights, so the fair price is the mid: (278.55 + 278.59) / 2 = 278.57
        assertThat(new DecayingLevelPrice(3, 0.5).fairPrice(book).orElseThrow())
                .isCloseTo(278.57, within(1e-9));
    }

    @Test
    void oneSidedBookHasNoFairPrice() {
        OrderBook onlyBids = new OrderBook("AAPL");
        ExampleBooks.add(onlyBids, 1, BookSide.BID, 2785500, 100);

        assertThat(new DecayingLevelPrice(3, 0.5).fairPrice(onlyBids)).isEmpty();
    }

    @Test
    void parametersMustBeInRange() {
        assertThatThrownBy(() -> new DecayingLevelPrice(0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecayingLevelPrice(3, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecayingLevelPrice(3, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
