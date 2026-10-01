package org.example.execution;

import org.example.order.Side;

import java.time.LocalDateTime;

public record Fill(String symbol, Side side, int quantity, double price, LocalDateTime time) {
}