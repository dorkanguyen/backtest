package org.example.order;

public record Order(String symbol, Side side, int quantity) {
}