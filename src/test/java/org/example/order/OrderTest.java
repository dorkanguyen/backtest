package org.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void marketOrderHasNoLimitPrice() {
        Order order = Order.market(1, "AAPL", Side.BUY, 100);

        assertThat(order.type()).isEqualTo(OrderType.MARKET);
        assertThat(order.limitPrice()).isZero();
    }

    @Test
    void limitOrderKeepsItsPrice() {
        Order order = Order.limit(7, "AAPL", Side.SELL, 50, 2785900);

        assertThat(order.id()).isEqualTo(7);
        assertThat(order.type()).isEqualTo(OrderType.LIMIT);
        assertThat(order.limitPrice()).isEqualTo(2785900);
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> Order.market(1, "AAPL", Side.BUY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitOrderNeedsPositivePrice() {
        assertThatThrownBy(() -> Order.limit(1, "AAPL", Side.BUY, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marketOrderMustNotHavePrice() {
        assertThatThrownBy(() -> new Order(1, "AAPL", Side.BUY, OrderType.MARKET, 100, 2785900))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
