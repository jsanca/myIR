# Spring Security Patterns

Match the project's Spring Boot generation (2.x vs 3+/4+) and existing security style before introducing new libraries.

## Stateless API With JWT Resource Server

Use when APIs are consumed by clients that send bearer tokens.

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

## Method-Level Authorization

Do not rely on controller path checks alone when service methods are reused.

```java
@Service
public class OrderService {

    @PreAuthorize("hasRole('ADMIN') or @orderAccess.canView(#orderId, authentication)")
    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .map(OrderMapper::toResponse)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

## Session-Based Web App With CSRF

Keep CSRF enabled for browser sessions that use cookies.

```java
@Bean
SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
    return http
        .csrf(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/css/**", "/js/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
}
```

## Security Review Signals

- `@PermitAll` or `permitAll()` on endpoints that mutate data
- Missing `@PreAuthorize` / `@RolesAllowed` on admin or owner-scoped operations
- Security config copied from a tutorial with CSRF disabled for cookie-based auth
- JWT validation without issuer, audience, or clock skew checks configured
- Actuator endpoints exposed without authentication in production profiles
