FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

# The ledger module depends on sibling reactor modules, so the repository root
# must be the Docker build context.
COPY . .
RUN mvn -pl ledger-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre

RUN useradd --system --create-home --uid 10001 ledger
WORKDIR /app

COPY --from=builder /workspace/ledger-service/target/ledger-service-1.0-SNAPSHOT.jar app.jar

# Logback resolves its relative path from /app. Docker copies this directory
# into the initially empty named volume with ownership preserved.
RUN mkdir -p /app/logs/ledger-service \
    && chown -R ledger:ledger /app/logs/ledger-service

USER ledger
EXPOSE 8081 9191

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
