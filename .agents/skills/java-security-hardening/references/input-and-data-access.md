# Input Validation and Safe Data Access

Validate at API boundaries. Treat all external input as untrusted, including headers, query params, path variables, message payloads, and uploaded files.

## Request Validation With Jakarta Validation

```java
public record CreateUserRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Min(18) @Max(120) Integer age
) {
}
```

```java
@PostMapping("/api/v1/users")
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
}
```

## Parameterized JPA Queries

Never build query strings from user input.

```java
// Unsafe — do not do this
@Query("SELECT u FROM User u WHERE u.email = '" + email + "'")

// Safe — named parameter
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);
```

```java
// Safe — Spring Data derived query with bound parameter
List<Order> findByStatusAndCreatedAtAfter(OrderStatus status, Instant createdAfter);
```

## Native SQL With Named Parameters

```java
public List<AuditRow> searchAudit(String action, Instant since, int limit) {
    return jdbcClient.sql("""
            SELECT id, action, created_at
            FROM audit_event
            WHERE action = :action
              AND created_at >= :since
            ORDER BY created_at DESC
            LIMIT :limit
            """)
        .param("action", action)
        .param("since", since)
        .param("limit", limit)
        .query(AuditRow.class)
        .list();
}
```

## Path and File Access

- Reject path traversal (`../`, absolute paths) in user-supplied file names.
- Resolve files under a known base directory and verify the resolved path stays inside it.
- Do not pass unsanitized input to `Runtime.exec`, script engines, or dynamic class loading.

## Deserialization and Object Mapping

- Avoid deserializing untrusted Java serialized objects.
- Restrict polymorphic JSON types unless the use case requires them and a strict allowlist exists.
- Do not enable default typing in Jackson without an explicit threat model review.
