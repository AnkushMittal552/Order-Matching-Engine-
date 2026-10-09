package com.trading.engine.book;

import com.trading.engine.model.Order;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Holds the resting (unmatched) orders for a single symbol, split into
 * two sides.
 *
 * PHASE 2 CHANGE FROM v1:
 * ------------------------
 * In Phase 1, cancellation was O(k) because it called Deque.remove(order),
 * which has to linearly scan the deque to find the matching element.
 * That's fine for shallow books but degrades on a hot price level with
 * thousands of resting orders (which happens in practice around round
 * numbers).
 *
 * Here, each price level is an intrusive doubly-linked list (Node has
 * prev/next pointers baked into it). orderIndex maps orderId -> Node
 * directly. Cancelling means: look up the Node in O(1), then unlink it
 * from its neighbors in O(1) -- no scanning required, regardless of how
 * many orders sit at that price level.
 *
 * WHY THESE DATA STRUCTURES OVERALL:
 *  - bids: TreeMap<price, PriceLevel> ordered HIGHEST price first
 *          (price priority for buyers).
 *  - asks: TreeMap<price, PriceLevel> ordered LOWEST price first
 *          (price priority for sellers).
 *  - PriceLevel: doubly-linked list of Nodes -> O(1) push front/back,
 *          O(1) pop front, O(1) removal of an arbitrary node given a
 *          reference to it (this is the FIFO / time-priority structure).
 *  - orderIndex: HashMap<orderId, Node> -> O(1) lookup for cancellation.
 *
 * TreeMap still gives O(log n) for "find/insert/remove a price level" --
 * that part hasn't changed, and there's no way to beat O(log n) there
 * with a comparison-based sorted structure. The optimization here is
 * specifically about removing an order WITHIN a level.
 */
public class OrderBook {

    /** One entry in a price level's doubly-linked list. */
    private static final class Node {
        Order order;
        Node prev;
        Node next;
        PriceLevel level; // back-reference, needed to know which level to clean up on removal

        Node(Order order) {
            this.order = order;
        }
    }

    /** A single price point's FIFO queue of orders, as an intrusive doubly-linked list. */
    private static final class PriceLevel {
        final BigDecimal price;
        Node head;
        Node tail;
        int size;

        PriceLevel(BigDecimal price) {
            this.price = price;
        }

        boolean isEmpty() {
            return head == null;
        }

        void addLast(Node node) {
            node.level = this;
            if (tail == null) {
                head = tail = node;
            } else {
                tail.next = node;
                node.prev = tail;
                tail = node;
            }
            size++;
        }

        void addFirst(Node node) {
            node.level = this;
            if (head == null) {
                head = tail = node;
            } else {
                head.prev = node;
                node.next = head;
                head = node;
            }
            size++;
        }

        /** O(1): removes and returns the front (oldest) order. */
        Node pollFirst() {
            if (head == null) return null;
            Node node = head;
            head = head.next;
            if (head != null) {
                head.prev = null;
            } else {
                tail = null;
            }
            node.next = null;
            size--;
            return node;
        }

        /** O(1): unlinks an arbitrary node given a direct reference to it. */
        void remove(Node node) {
            Node prev = node.prev;
            Node next = node.next;
            if (prev != null) prev.next = next; else head = next;
            if (next != null) next.prev = prev; else tail = prev;
            node.prev = null;
            node.next = null;
            size--;
        }
    }

    private final String symbol;

    private final TreeMap<BigDecimal, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, PriceLevel> asks = new TreeMap<>(Comparator.naturalOrder());

    // O(1) lookup for cancellation: orderId -> the exact linked-list Node holding it.
    private final Map<Long, Node> orderIndex = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private TreeMap<BigDecimal, PriceLevel> bookForSide(Order.Side side) {
        return side == Order.Side.BUY ? bids : asks;
    }

    /** Adds a resting order to the book (called after matching leaves a remainder). */
    public void addOrder(Order order) {
        TreeMap<BigDecimal, PriceLevel> book = bookForSide(order.getSide());
        PriceLevel level = book.computeIfAbsent(order.getPrice(), PriceLevel::new);
        Node node = new Node(order);
        level.addLast(node);
        orderIndex.put(order.getOrderId(), node);
    }

    public BigDecimal bestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public BigDecimal bestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /** Peeks the oldest (highest time-priority) order at the best opposite price. */
    public Order peekBest(Order.Side side) {
        TreeMap<BigDecimal, PriceLevel> book = bookForSide(side);
        if (book.isEmpty()) return null;
        PriceLevel level = book.firstEntry().getValue();
        return level.head == null ? null : level.head.order;
    }

    /**
     * Removes and returns the front order at the best price on the given side.
     * O(log n) to find the price level (TreeMap), O(1) to pop the front node.
     */
    public Order pollBest(Order.Side side) {
        TreeMap<BigDecimal, PriceLevel> book = bookForSide(side);
        if (book.isEmpty()) return null;

        Map.Entry<BigDecimal, PriceLevel> entry = book.firstEntry();
        PriceLevel level = entry.getValue();
        Node node = level.pollFirst();
        if (node == null) return null;

        if (level.isEmpty()) {
            book.remove(entry.getKey());
        }
        orderIndex.remove(node.order.getOrderId());
        return node.order;
    }

    /**
     * Puts a partially-filled resting order back at the FRONT of its price
     * level -- it must not lose its time priority just because it was
     * temporarily popped off during matching.
     */
    public void requeueAtFront(Order order) {
        TreeMap<BigDecimal, PriceLevel> book = bookForSide(order.getSide());
        PriceLevel level = book.computeIfAbsent(order.getPrice(), PriceLevel::new);
        Node node = new Node(order);
        level.addFirst(node);
        orderIndex.put(order.getOrderId(), node);
    }

    /**
     * Cancels a resting order by id.
     * O(1) lookup (HashMap) + O(1) unlink (doubly-linked list) + O(log n)
     * only in the rare case the level becomes empty and must be removed
     * from the TreeMap. This is the Phase 2 improvement over Phase 1's
     * O(k) Deque.remove().
     */
    public boolean cancelOrder(long orderId) {
        Node node = orderIndex.remove(orderId);
        if (node == null) {
            return false; // not found / already filled or cancelled
        }
        PriceLevel level = node.level;
        level.remove(node);
        if (level.isEmpty()) {
            TreeMap<BigDecimal, PriceLevel> book = bookForSide(node.order.getSide());
            book.remove(level.price);
        }
        return true;
    }

    public Order getOrder(long orderId) {
        Node node = orderIndex.get(orderId);
        return node == null ? null : node.order;
    }

    /**
     * Returns a read-only snapshot of book depth for display purposes.
     * This is intentionally O(n) -- it's only used for UI/reporting, never
     * on the hot path of matching or cancellation, so it doesn't need the
     * same performance guarantees as addOrder/pollBest/cancelOrder.
     */
    public Map<BigDecimal, Long> depthSnapshot(Order.Side side) {
        TreeMap<BigDecimal, PriceLevel> book = bookForSide(side);
        Comparator<BigDecimal> ordering = side == Order.Side.BUY
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        Map<BigDecimal, Long> snapshot = new TreeMap<>(ordering);
        for (Map.Entry<BigDecimal, PriceLevel> e : book.entrySet()) {
            long totalQty = 0;
            Node cursor = e.getValue().head;
            while (cursor != null) {
                totalQty += cursor.order.getQuantity();
                cursor = cursor.next;
            }
            snapshot.put(e.getKey(), totalQty);
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public boolean isSideEmpty(Order.Side side) {
        return bookForSide(side).isEmpty();
    }

    /** Number of individual resting orders on one side of the book, for UI statistics. */
    public long restingOrderCount(Order.Side side) {
        long count = 0;
        for (PriceLevel level : bookForSide(side).values()) {
            for (Node cursor = level.head; cursor != null; cursor = cursor.next) {
                count++;
            }
        }
        return count;
    }
}
