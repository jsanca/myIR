# Unit Test Patterns

Unit tests prove logic in isolation. No database, no network, no Spring context unless the class under test truly requires it.

## Naming and Structure

```java
@Test
void rejectsOrderWhenInventoryIsInsufficient() {
    var inventory = mock(InventoryPort.class);
    when(inventory.available("SKU-1")).thenReturn(0);

    var service = new OrderService(inventory);

    assertThatThrownBy(() -> service.placeOrder(new OrderLine("SKU-1", 1)))
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessageContaining("SKU-1");
}
```

Use names that describe scenario and expected outcome.

## Parameterized Edge Cases

```java
@ParameterizedTest
@CsvSource({
    "0, false",
    "1, true",
    "100, true"
})
void acceptsPositiveQuantities(int quantity, boolean valid) {
    assertThat(OrderLine.isValidQuantity(quantity)).isEqualTo(valid);
}
```

## Regression Test for Bug Fixes

Always add a test that fails on the old behavior.

```java
@Test
void doesNotDoubleApplyDiscountWhenCouponAlreadyApplied() {
    var order = new Order(BigDecimal.valueOf(100));
    order.applyCoupon("SAVE10");
    order.applyCoupon("SAVE10");

    assertThat(order.total()).isEqualByComparingTo("90.00");
}
```

## Mock Only Boundaries You Own

Prefer fakes or stubs for simple ports. Mock external systems at the boundary, not every collaborator.

```java
class FakeClock implements Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public Instant instant() {
        return now;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }
}
```

## Commands

Gradle:

```bash
./gradlew test --tests "com.example.orders.OrderServiceTest"
```

Maven:

```bash
mvn -q test -Dtest=OrderServiceTest
```
