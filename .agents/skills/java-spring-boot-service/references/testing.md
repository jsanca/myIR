# Spring Boot Testing

Choose the narrowest useful test.

## Unit Test for Service Logic

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @InjectMocks
    private OrderService service;

    @Test
    void createsOrderWithGeneratedId() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(new CreateOrderRequest("SKU-1", 2));

        assertThat(response.id()).isNotBlank();
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
    }
}
```

## MVC Slice Test

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void rejectsInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"SKU-1","quantity":0}
                    """))
            .andExpect(status().isBadRequest());
    }
}
```

## Test Strategy by Layer

| Layer | Test type | When to use |
| --- | --- | --- |
| Pure service logic | Unit test with mocks/fakes | Business rules, mapping, validation |
| Controller mapping and validation | `@WebMvcTest` | HTTP status, JSON shape, validation errors |
| Repository queries | `@DataJpaTest` | JPQL, constraints, custom queries |
| Cross-layer flow | `@SpringBootTest` | End-to-end behavior across layers |
| Real database semantics | Testcontainers | Migrations, indexes, isolation levels |

## Commands

Gradle:

```bash
./gradlew test --tests "com.example.orders.*"
```

Maven:

```bash
mvn -q test -Dtest=Order*Test
```

Avoid loading the full application context when a smaller test proves the behavior.
