package com.trading.engine;

import com.trading.engine.engine.MatchingEngine;
import com.trading.engine.model.Order;
import com.trading.engine.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine("AAPL");
    }

    private Order order(Order.Side side, String price, long qty) {
        return new Order(engine.nextOrderId(), "AAPL", side, new BigDecimal(price), qty);
    }

    // ---------- Basic resting / no-match behavior ----------

    @Test
    void restingOrdersDoNotMatchWhenPricesDoNotCross() {
        engine.submitOrder(order(Order.Side.SELL, "150.00", 100));
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "149.00", 100));

        assertTrue(trades.isEmpty(), "No trade should occur when bid < ask");
        assertEquals(new BigDecimal("150.00"), engine.getOrderBook().bestAskPrice());
        assertEquals(new BigDecimal("149.00"), engine.getOrderBook().bestBidPrice());
    }

    // ---------- Exact match ----------

    @Test
    void exactQuantityMatchEmptiesBothOrdersFromBook() {
        engine.submitOrder(order(Order.Side.SELL, "150.00", 100));
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 100));

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getQuantity());
        assertEquals(new BigDecimal("150.00"), trades.get(0).getPrice());
        assertNull(engine.getOrderBook().bestAskPrice(), "Ask side should be empty after exact match");
        assertNull(engine.getOrderBook().bestBidPrice(), "Bid side should be empty after exact match");
    }

    // ---------- Partial fills ----------

    @Test
    void incomingSmallerThanRestingLeavesRestingPartiallyFilled() {
        engine.submitOrder(order(Order.Side.SELL, "150.00", 100));
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 30));

        assertEquals(1, trades.size());
        assertEquals(30, trades.get(0).getQuantity());

        // 70 shares should remain resting on the ask side
        Map<BigDecimal, Long> askDepth = engine.getOrderBook().depthSnapshot(Order.Side.SELL);
        assertEquals(70L, askDepth.get(new BigDecimal("150.00")));
        assertNull(engine.getOrderBook().bestBidPrice(), "Incoming buy order should be fully consumed, nothing rests");
    }

    @Test
    void incomingLargerThanRestingRestsTheRemainder() {
        engine.submitOrder(order(Order.Side.SELL, "150.00", 40));
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 100));

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());

        assertNull(engine.getOrderBook().bestAskPrice(), "Ask side fully consumed");
        Map<BigDecimal, Long> bidDepth = engine.getOrderBook().depthSnapshot(Order.Side.BUY);
        assertEquals(60L, bidDepth.get(new BigDecimal("150.00")), "60 shares of the buy order should rest");
    }

    // ---------- Multi-level sweep ----------

    @Test
    void incomingOrderSweepsMultiplePriceLevels() {
        engine.submitOrder(order(Order.Side.SELL, "149.00", 50));
        engine.submitOrder(order(Order.Side.SELL, "150.00", 100));

        // Willing to pay up to 150 for 100 shares -> takes all 50 @149, then 50 of 100 @150
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 100));

        assertEquals(2, trades.size(), "Should generate two trades, one per price level");
        assertEquals(new BigDecimal("149.00"), trades.get(0).getPrice());
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(new BigDecimal("150.00"), trades.get(1).getPrice());
        assertEquals(50, trades.get(1).getQuantity());

        Map<BigDecimal, Long> askDepth = engine.getOrderBook().depthSnapshot(Order.Side.SELL);
        assertEquals(50L, askDepth.get(new BigDecimal("150.00")), "50 shares should remain at the 150 level");
        assertFalse(askDepth.containsKey(new BigDecimal("149.00")), "149 level should be fully drained and removed");
    }

    // ---------- Trade price convention ----------

    @Test
    void tradeExecutesAtRestingOrdersPriceNotAggressorsPrice() {
        // Resting seller only demands 148, but the aggressive buyer offers up to 150.
        // The trade should clear at 148 (the resting/passive party's price), not 150.
        engine.submitOrder(order(Order.Side.SELL, "148.00", 50));
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 50));

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("148.00"), trades.get(0).getPrice(),
                "Trade must clear at the resting seller's price, giving the buyer price improvement");
    }

    // ---------- Price-time priority (FIFO within a level) ----------

    @Test
    void ordersAtSamePriceAreMatchedInArrivalOrder() {
        Order firstSeller = order(Order.Side.SELL, "150.00", 50);
        Order secondSeller = order(Order.Side.SELL, "150.00", 50);
        engine.submitOrder(firstSeller);
        engine.submitOrder(secondSeller);

        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 50));

        assertEquals(1, trades.size());
        assertEquals(firstSeller.getOrderId(), trades.get(0).getSellOrderId(),
                "The seller who arrived first at this price level must be matched first");
    }

    // ---------- Cancellation ----------

    @Test
    void cancellingRestingOrderRemovesItFromBook() {
        Order resting = order(Order.Side.BUY, "148.00", 80);
        engine.submitOrder(resting);

        assertTrue(engine.cancelOrder(resting.getOrderId()));
        assertNull(engine.getOrderBook().bestBidPrice(), "Book should be empty after cancelling its only order");
    }

    @Test
    void cancellingNonexistentOrderReturnsFalse() {
        assertFalse(engine.cancelOrder(999999L));
    }

    @Test
    void cancellingAlreadyFullyFilledOrderReturnsFalse() {
        Order seller = order(Order.Side.SELL, "150.00", 50);
        engine.submitOrder(seller);
        engine.submitOrder(order(Order.Side.BUY, "150.00", 50)); // fully consumes seller

        assertFalse(engine.cancelOrder(seller.getOrderId()),
                "An order that has already been fully matched and removed cannot be cancelled");
    }

    @Test
    void cancellingMiddleOrderPreservesFifoOrderOfRemainingOrders() {
        // This specifically exercises the Phase 2 O(1) doubly-linked-list removal:
        // cancelling a node in the middle of the list must correctly re-link its
        // neighbors, not just blank it out.
        Order first = order(Order.Side.SELL, "150.00", 10);
        Order second = order(Order.Side.SELL, "150.00", 10);
        Order third = order(Order.Side.SELL, "150.00", 10);
        engine.submitOrder(first);
        engine.submitOrder(second);
        engine.submitOrder(third);

        assertTrue(engine.cancelOrder(second.getOrderId()));

        // Now match against remaining resting orders; first should still go first,
        // then third -- confirming the linked list re-stitched correctly around
        // the removed middle node.
        List<Trade> trades = engine.submitOrder(order(Order.Side.BUY, "150.00", 20));

        assertEquals(2, trades.size());
        assertEquals(first.getOrderId(), trades.get(0).getSellOrderId());
        assertEquals(third.getOrderId(), trades.get(1).getSellOrderId());
    }

    // ---------- Depth snapshot correctness ----------

    @Test
    void depthSnapshotAggregatesQuantityAcrossMultipleOrdersAtSameLevel() {
        engine.submitOrder(order(Order.Side.SELL, "150.00", 30));
        engine.submitOrder(order(Order.Side.SELL, "150.00", 20));

        Map<BigDecimal, Long> askDepth = engine.getOrderBook().depthSnapshot(Order.Side.SELL);
        assertEquals(50L, askDepth.get(new BigDecimal("150.00")));
    }

    @Test
    void depthSnapshotOrdersBidsHighestFirstAndAsksLowestFirst() {
        engine.submitOrder(order(Order.Side.BUY, "148.00", 10));
        engine.submitOrder(order(Order.Side.BUY, "149.00", 10));
        engine.submitOrder(order(Order.Side.SELL, "152.00", 10));
        engine.submitOrder(order(Order.Side.SELL, "151.00", 10));

        Object[] bidPrices = engine.getOrderBook().depthSnapshot(Order.Side.BUY).keySet().toArray();
        Object[] askPrices = engine.getOrderBook().depthSnapshot(Order.Side.SELL).keySet().toArray();

        assertEquals(new BigDecimal("149.00"), bidPrices[0], "Highest bid should come first");
        assertEquals(new BigDecimal("148.00"), bidPrices[1]);
        assertEquals(new BigDecimal("151.00"), askPrices[0], "Lowest ask should come first");
        assertEquals(new BigDecimal("152.00"), askPrices[1]);
    }
}
