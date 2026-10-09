package com.trading.engine.util;

import com.trading.engine.book.OrderBook;
import com.trading.engine.engine.MatchingEngine;
import com.trading.engine.model.Order;
import com.trading.engine.model.Trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ConsolePrinter {

    public static void printHeader(String symbol) {
        System.out.println();
        System.out.println("==============================================================");
        System.out.println("           ORDER MATCHING ENGINE DEMONSTRATION");
        System.out.println("==============================================================");
        System.out.println("Matching Strategy : Price-Time Priority");
        System.out.println("Trading Symbol    : " + symbol);
        System.out.println("==============================================================");
    }

    public static void printStep(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println("--------------------------------------------------------------");
    }

    public static void printOrder(Order order) {

        System.out.printf(
                "%-8d %-6s %-10d %-10s %-10s%n",
                order.getOrderId(),
                order.getSide(),
                order.getOriginalQuantity(),
                order.getPrice(),
                "ADDED"
        );
    }

    public static void printTrades(List<Trade> trades) {

        if (trades.isEmpty()) {
            System.out.println("No trades executed.");
            return;
        }

        System.out.println();
        System.out.println("Trades Executed");

        System.out.printf(
                "%-8s %-10s %-10s %-10s %-10s%n",
                "Trade",
                "Qty",
                "Price",
                "Buyer",
                "Seller");

        for (Trade trade : trades) {

            System.out.printf(
                    "%-8d %-10d %-10s %-10d %-10d%n",
                    trade.getTradeId(),
                    trade.getQuantity(),
                    trade.getPrice(),
                    trade.getBuyOrderId(),
                    trade.getSellOrderId());
        }
    }

    public static void printOrderBook(OrderBook book) {

        System.out.println();
        System.out.println("Current Order Book");
        System.out.println();

        System.out.println("SELL ORDERS");

        printDepth(book.depthSnapshot(Order.Side.SELL));

        System.out.println();

        System.out.println("BUY ORDERS");

        printDepth(book.depthSnapshot(Order.Side.BUY));
    }

    private static void printDepth(Map<BigDecimal, Long> depth) {

        System.out.printf("%-10s %-10s%n", "Price", "Quantity");

        if (depth.isEmpty()) {
            System.out.println("(empty)");
            return;
        }

        for (Map.Entry<BigDecimal, Long> entry : depth.entrySet()) {

            System.out.printf(
                    "%-10s %-10d%n",
                    entry.getKey(),
                    entry.getValue());
        }
    }

    public static void printTradeHistory(MatchingEngine engine) {

        printStep("STEP 4 : TRADE HISTORY");

        List<Trade> trades = engine.getTradeLog();

        if (trades.isEmpty()) {
            System.out.println("No trades.");
            return;
        }

        System.out.printf(
                "%-8s %-8s %-10s %-10s%n",
                "Trade",
                "Symbol",
                "Qty",
                "Price");

        for (Trade trade : trades) {

            System.out.printf(
                    "%-8d %-8s %-10d %-10s%n",
                    trade.getTradeId(),
                    trade.getSymbol(),
                    trade.getQuantity(),
                    trade.getPrice());
        }
    }

    public static void printCancel(long orderId, boolean success) {

        printStep("STEP 5 : CANCEL ORDER");

        System.out.println("Cancelled Order : " + orderId);

        System.out.println("Status          : "
                + (success ? "SUCCESS" : "FAILED"));
    }

    public static void printStatistics(MatchingEngine engine) {

        printStep("STEP 6 : FINAL STATISTICS");

        long totalTrades = engine.getTradeLog().size();

        long volume = 0;

        for (Trade trade : engine.getTradeLog()) {
            volume += trade.getQuantity();
        }

        System.out.println("Orders Submitted : 6");
        System.out.println("Trades Executed  : " + totalTrades);
        System.out.println("Shares Traded    : " + volume);
        System.out.println("Engine Status    : SUCCESS");
    }
}