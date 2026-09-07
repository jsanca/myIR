# Transactions, Locking, and Batch Writes

## Keep Transactions Short

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    public OrderService(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(OrderMapper.toEntity(request));
        return OrderMapper.toResponse(order);
    }

    public OrderResponse placeOrderWithPayment(CreateOrderRequest request) {
        OrderResponse created = placeOrder(request);
        paymentGateway.charge(created.id(), request.paymentToken());
        return created;
    }
}
```

External I/O (HTTP, messaging, file storage) should not run inside a DB transaction unless there is a deliberate saga or outbox design.

## Read-Only Queries

```java
@Transactional(readOnly = true)
public Page<OrderSummaryResponse> listOrders(UUID customerId, Pageable pageable) {
    return orderRepository.summarizeByCustomer(customerId, pageable);
}
```

## Optimistic Locking

```java
@Entity
public class InventoryItem {

    @Version
    private long version;

    // ...
}
```

```java
@Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3)
@Transactional
public void adjustStock(UUID itemId, int delta) {
    InventoryItem item = inventoryRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
    item.adjustQuantity(delta);
}
```

## Batch Inserts and Updates

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

```java
@Transactional
public void importRows(List<ImportRow> rows) {
    for (int i = 0; i < rows.size(); i++) {
        catalogRepository.save(CatalogMapper.toEntity(rows.get(i)));
        if (i > 0 && i % 50 == 0) {
            catalogRepository.flush();
            catalogRepository.clearPersistenceContext();
        }
    }
}
```

Match batch size to pool capacity and DB driver limits. Very large batches in one transaction can hold locks and bloat the persistence context.
