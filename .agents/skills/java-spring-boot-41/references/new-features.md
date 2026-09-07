# Spring Boot 4.1 New Features

Adopt these when the user task requires them. Boot 4.1 includes fixes and dependency upgrades from the 4.0.x line — read release notes for security patches.

## Spring gRPC

Boot 4.1 adds **first-class gRPC support**. When adding gRPC services or clients:

- Use Boot-managed starters and configuration properties from the 4.1 BOM.
- Define `.proto` files, generated stubs, and server/client beans in separate commits when possible.
- Configure TLS/mTLS and authentication consistently with existing Security setup.
- Add integration tests that start the gRPC server (or use test slices documented in the project).

Do not add gRPC alongside REST for the same operation without an explicit architectural reason.

## Jackson Configuration

4.1 updates **Jackson configuration properties** and customization hooks. When customizing JSON:

- Prefer Boot 4.1 property namespaces over copied 4.0 examples.
- Register modules through Boot auto-configuration when available.
- Re-run JSON contract tests after changing date, null, or polymorphic typing settings.

## HTTP Client SSRF Mitigation

Boot 4.1 introduces **`InetAddressFilter`** for HTTP clients to reduce SSRF risk on outbound calls.

Use when:

- The app calls user-supplied URLs or dynamic hosts
- WebClient or RestClient fetches internal-adjacent resources

Example pattern (adapt to project APIs):

```java
@Bean
ClientHttpRequestFactory requestFactory() {
    var factory = new JdkClientHttpRequestFactory();
    factory.setInetAddressFilter(InetAddressFilter.allow(
        InetAddressFilter.AddressType.PUBLIC,
        InetAddressFilter.AddressType.UNICAST_GLOBAL));
    return factory;
}
```

Combine with application-level URL allowlists — filters are defense in depth, not the only control.

## Observability

OpenTelemetry support received updates in 4.1. After upgrade:

- Confirm trace propagation through HTTP and messaging boundaries
- Validate metric export to Prometheus, OTLP, or vendor backends in staging
- Check structured logging correlation IDs still bind to spans

See `java-cloud-native-delivery` and `observability-review` for broader production checks.

## Log4j File Rotation

**File rotation support for Log4j** landed in 4.1. When the project uses Log4j2 (not Logback):

- Move rotation settings into supported Boot properties instead of ad-hoc appenders where possible
- Verify disk paths and retention in containerized deployments

## Dependency Highlights (4.1.0)

| Dependency | Typical version |
| --- | --- |
| Spring Framework | 7.0.8 |
| Spring Security | 7.1.0 |
| Spring Session | 4.1.0 |

Always defer to the resolved versions in `./gradlew dependencies` or `mvn dependency:tree`.

## Verification

```bash
./gradlew clean check
./mvn -q verify
```

For gRPC:

- Run service-specific integration tests
- Confirm health and reflection (if enabled) in non-production only

For SSRF-sensitive features:

- Add tests that reject link-local or metadata IP ranges when user URLs are involved

## Related Skills

- `java-spring-boot-service` — REST vertical slices
- `java-security-hardening` — outbound call and auth review
- `java-spring-boot-40` — baseline Boot 4 migration
