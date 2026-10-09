package com.trading.engine.model;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents one executed trade: a resting order was matched against
 * an incoming (aggressor) order at a specific price and quantity.
 *
 * Convention used here: the trade executes at the RESTING order's price
 * (the order that was already sitting in the book), since that's the
 * price the resting party committed to. This mirrors real exchanges.
 */
public class Trade {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final long tradeId;
    private final String symbol;
    private final long buyOrderId;
    private final long sellOrderId;
    private final BigDecimal price;
    private final long quantity;
    private final long timestamp;

    public Trade(String symbol, long buyOrderId, long sellOrderId, BigDecimal price, long quantity) {
        this.tradeId = ID_GENERATOR.incrementAndGet();
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.nanoTime();
    }

    public long getTradeId() {
        return tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getBuyOrderId() {
        return buyOrderId;
    }

    public long getSellOrderId() {
        return sellOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Trade#%d[%s %d@%s  buyOrder=%d sellOrder=%d]",
                tradeId, symbol, quantity, price, buyOrderId, sellOrderId);
    }
}
