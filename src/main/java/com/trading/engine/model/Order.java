package com.trading.engine.model;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single limit order (buy or sell) in the exchange.
 *
 * Quantity is mutable because an order can be PARTIALLY filled over its
 * lifetime (e.g. a 100-share buy order might get filled 60 shares now,
 * 40 shares later against a different resting sell order).
 *
 * sequenceNumber gives us strict arrival ordering for time-priority,
 * which is more reliable than relying purely on nanoTime timestamps
 * (two orders can legitimately share a timestamp on fast hardware).
 */
public class Order {

    // Monotonically increasing counter -> guarantees a strict, gapless
    // arrival order across ALL orders, independent of clock resolution.
    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

    public enum Side {
        BUY,
        SELL
    }

    private final long orderId;
    private final long sequenceNumber;
    private final String symbol;
    private final Side side;
    private final BigDecimal price;
    private long quantity; // remaining (unfilled) quantity
    private final long originalQuantity;
    private final long timestamp;

    public Order(long orderId, String symbol, Side side, BigDecimal price, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        this.orderId = orderId;
        this.sequenceNumber = SEQUENCE_GENERATOR.incrementAndGet();
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.timestamp = System.nanoTime();
    }

    public long getOrderId() {
        return orderId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getOriginalQuantity() {
        return originalQuantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isFullyFilled() {
        return quantity == 0;
    }

    /**
     * Reduces the remaining quantity after a (partial) fill.
     * Called by the MatchingEngine during trade execution.
     */
    public void reduceQuantity(long filledQty) {
        if (filledQty > quantity) {
            throw new IllegalStateException(
                "Cannot fill " + filledQty + " on order " + orderId + " with only " + quantity + " remaining");
        }
        this.quantity -= filledQty;
    }

    @Override
    public String toString() {
        return String.format("Order#%d[%s %s %d@%s remaining=%d]",
                orderId, symbol, side, originalQuantity, price, quantity);
    }
}
