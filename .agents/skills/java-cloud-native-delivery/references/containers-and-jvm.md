# Containers and JVM Settings

## Multi-Stage Dockerfile (Spring Boot JAR)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle* build.gradle* ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

Adjust paths for Maven (`target/*.jar`) and project layout. Build the JAR in CI when reproducibility matters more than Dockerfile simplicity.

## Container-Aware JVM Flags

Common starting point inside containers:

```text
-XX:MaxRAMPercentage=75.0
-XX:InitialRAMPercentage=50.0
-XX:+ExitOnOutOfMemoryError
```

For Java 21+ with virtual threads, still size heap for peak concurrent request memory — virtual threads reduce thread stack cost, not all allocation pressure.

## Local Run With Env Config

```bash
docker build -t orders-service:local .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/orders \
  -e SPRING_DATASOURCE_USERNAME=orders \
  -e SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
  orders-service:local
```

Never bake database passwords into the image or compose file committed to git.

## Image Hygiene

- Use slim JRE base images.
- Pin base image digests in production pipelines when your registry supports it.
- Run as non-root.
- Expose only required ports.
- Add `.dockerignore` for `target/`, `build/`, `.git`, and local env files.
