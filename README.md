# Order Matching Engine — Phase 2

## What changed from Phase 1

`OrderBook` was rewritten so that **cancellation is true O(1)**, not O(k).

- Phase 1: each price level was an `ArrayDeque<Order>`. Cancelling meant
  calling `Deque.remove(order)`, which linearly scans the deque to find
  the element — O(k) where k = orders resting at that price level.
- Phase 2: each price level is now an **intrusive doubly-linked list**
  (a private `Node` class with `prev`/`next` pointers baked in). The
  `orderIndex` map now stores `orderId -> Node` directly. Cancelling an
  order means: look up its `Node` in O(1), then unlink it from its
  neighbors in O(1) — no scanning, regardless of how deep the price
  level is.

`depthSnapshot()` (used only for display/reporting) is still O(n) by
design — it's off the hot path, so there's no reason to pay for cached
aggregate tracking there.

## Project layout (Maven standard)

```
pom.xml
src/main/java/com/trading/engine/
  ├── model/Order.java
  ├── model/Trade.java
  ├── book/OrderBook.java       <- rewritten in Phase 2
  ├── engine/MatchingEngine.java
  └── Main.java                  (console demo)
src/test/java/com/trading/engine/
  └── MatchingEngineTest.java    <- JUnit 5 test suite (12 tests)
```

## Running it

```
mvn test      # runs the JUnit 5 suite
mvn compile exec:java -Dexec.mainClass=com.trading.engine.Main   # or just run Main from your IDE
mvn package   # builds an executable jar at target/order-matching-engine-1.0.0.jar
```

## Test coverage

- Basic no-match / resting behavior
- Exact quantity matches
- Partial fills on both the resting and incoming side
- Multi-price-level sweeps
- Trade price convention (executes at the resting order's price)
- Price-time priority / FIFO within a price level
- Cancellation: success, not-found, already-filled
- **Cancelling a middle order in a price level** (regression test for the
  Phase 2 linked-list refactor — proves neighbors re-link correctly)
- Depth snapshot aggregation and ordering
