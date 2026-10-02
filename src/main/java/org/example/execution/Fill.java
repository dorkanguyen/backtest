package org.example.execution;

import org.example.order.Side;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Fill(String symbol, Side side, int quantity, BigDecimal price, LocalDateTime time) {
}