# Shared State and Locking

## Prefer Immutable Command Objects

```java
public record AdjustInventoryCommand(UUID itemId, int delta, String reason) {
    public AdjustInventoryCommand {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(reason, "reason");
    }
}
```

Pass commands through services instead of mutating shared DTOs across threads.

## Atomic Counters for Metrics Only

```java
private final LongAdder processedCount = new LongAdder();

public void onProcessed() {
    processedCount.increment();
}
```

Do not implement business invariants with atomics when domain rules belong in the database or a single-writer service path.

## Consistent Lock Ordering

```java
public void transfer(Account from, Account to, MoneyAmount amount) {
    Account first = from.id().compareTo(to.id()) < 0 ? from : to;
    Account second = first == from ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

When using `ReentrantLock`, use `tryLock` with timeout in services that must stay responsive:

```java
if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
    throw new ServiceUnavailableException("Could not acquire lock");
}
try {
    // critical section
} finally {
    lock.unlock();
}
```

## Database Is the Source of Truth for Cross-Request Consistency

In multi-instance services, in-memory locks do not coordinate across JVMs. Use transactional updates, optimistic versioning, or database constraints for inventory, balances, and idempotency keys.

```java
@Modifying
@Query("""
    UPDATE InventoryItem i
    SET i.quantity = i.quantity - :delta
    WHERE i.id = :id AND i.quantity >= :delta
    """)
int decrementIfAvailable(@Param("id") UUID id, @Param("delta") int delta);
```

Check affected row count — zero means concurrent conflict or insufficient stock.
