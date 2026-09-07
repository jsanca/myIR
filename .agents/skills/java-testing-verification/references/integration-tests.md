# Integration and Slice Tests

Use the narrowest test that still proves the boundary behavior.

## Spring MVC Slice Test

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void returnsCreatedOrder() throws Exception {
        when(orderService.create(any())).thenReturn(new OrderResponse("ORD-1", "CREATED"));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"SKU-1","quantity":1}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("ORD-1"));
    }
}
```

## JPA Slice Test

```java
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository repository;

    @Test
    void findsOrdersByStatus() {
        repository.save(new OrderEntity("ORD-1", OrderStatus.OPEN));

        var results = repository.findByStatus(OrderStatus.OPEN);

        assertThat(results).extracting(OrderEntity::getExternalId).containsExactly("ORD-1");
    }
}
```

## Testcontainers When SQL Behavior Matters

```java
@Testcontainers
@SpringBootTest
class OrderRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderRepository repository;

    @Test
    void persistsOrderWithConstraint() {
        repository.save(new OrderEntity("ORD-2", OrderStatus.OPEN));

        assertThat(repository.findByExternalId("ORD-2")).isPresent();
    }
}
```

## CI Verification Gates

Gradle:

```bash
./gradlew test integrationTest
```

Maven:

```bash
mvn -q verify
```

Fail the change if:

- New behavior has no test
- Tests depend on execution order
- Assertions check implementation details instead of observable outcomes
