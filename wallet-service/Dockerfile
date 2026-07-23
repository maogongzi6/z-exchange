FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

# The wallet module depends on sibling reactor modules, so the repository root
# must be the Docker build context.
COPY . .
RUN mvn -pl wallet-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre

RUN useradd --system --create-home --uid 10002 wallet
WORKDIR /app

COPY --from=builder /workspace/wallet-service/target/wallet-service-1.0-SNAPSHOT.jar app.jar

# Logback resolves its relative path from /app. Docker copies this directory
# into the initially empty named volume with ownership preserved.
RUN mkdir -p /app/logs/wallet-service \
    && chown -R wallet:wallet /app/logs/wallet-service

USER wallet
EXPOSE 8082 9192

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
