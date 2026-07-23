# Ledger Docker Deployment Guide

## Purpose

This deployment runs the ledger database, Redis, Kafka, ledger-service, Prometheus, and Grafana on one Docker host. The ledger container talks to its dependencies through the Docker bridge network; a locally running wallet-service talks to the cloud host through externally published Kafka, Redis, and gRPC ports.

The expected paths are:

```text
Local caller -> local wallet-service:9192
                         |
                         +-> cloud ledger-service:9191       (CreateWallet account creation)
                         |
                         +-> cloud Kafka -> cloud ledger-service
                                              |
Local wallet-service <- cloud Kafka reply <---+
```

Normal wallet posting is asynchronous: wallet writes its own DB transaction and outbox, its publisher sends `wallet.ledger.command.posting` to Kafka, ledger consumes it, then ledger publishes `ledger.wallet.reply.posting`. The wallet reply listener consumes that reply and completes the wallet transaction. The local wallet does not connect to the ledger MySQL database.

## Changed And Added Files

| File | Change | Why it is needed |
| --- | --- | --- |
| `ledger-service/Dockerfile` | New multi-stage Java 21 image. | Builds the Maven reactor dependencies, then runs only the executable ledger JAR as a non-root user. |
| `.dockerignore` | New Docker build exclusions. | Prevents Git history, IDE files, existing `target` directories, and documentation from being sent to the Docker builder. |
| `ledger-service/pom.xml` | Added `spring-boot-maven-plugin`. | Produces an executable Spring Boot JAR. The Docker runtime cannot launch the prior thin JAR by itself. |
| `ledger-service/src/main/resources/application-docker.yml` | New `docker` profile. | Replaces host-oriented addresses with Docker service DNS names and allows runtime overrides. |
| `docker-compose.yml` | Added MySQL and ledger-service; updated Redis, Kafka, network, health checks, and persistent volumes. | Starts the full ledger dependency set in the correct order and gives containers stable internal names. |
| `monitoring/prometheus.yml` | Ledger scrape target changed to `ledger-service:8081`. | Prometheus is also in Docker, so it must use Docker DNS rather than a host IP. |

The existing `vmware` and `cloud` Spring profiles remain unchanged. Compose selects the new profile with `SPRING_PROFILES_ACTIVE=docker`.

## How Configuration Values Work Together

Compose expands `${NAME:-default}` on the cloud host before a container is created. Spring expands `${NAME:default}` inside the ledger container when it starts.

For example, [application-docker.yml](../ledger-service/src/main/resources/application-docker.yml) contains:

```yaml
username: ${DB_USERNAME:ledger}
```

This means: use the container environment variable `DB_USERNAME`; if it is absent, use `ledger`.

Compose provides that value to ledger-service and creates the matching MySQL account:

```yaml
mysql:
  environment:
    MYSQL_USER: ${LEDGER_DB_USERNAME:-ledger}
    MYSQL_PASSWORD: ${LEDGER_DB_PASSWORD:-ledger-local}

ledger-service:
  environment:
    DB_USERNAME: ${LEDGER_DB_USERNAME:-ledger}
    DB_PASSWORD: ${LEDGER_DB_PASSWORD:-ledger-local}
```

Consequently, setting `LEDGER_DB_USERNAME=ledger_app` changes both `MYSQL_USER` and `DB_USERNAME` to `ledger_app`. Setting `LEDGER_DB_PASSWORD` changes both the created MySQL user's password and the application's JDBC password. Those two values must always match.

| Host variable | Default | Used by | Meaning |
| --- | --- | --- | --- |
| `LEDGER_DB_USERNAME` | `ledger` | `MYSQL_USER`, `DB_USERNAME` | Non-root MySQL account used by ledger-service. |
| `LEDGER_DB_PASSWORD` | `ledger-local` | `MYSQL_PASSWORD`, `DB_PASSWORD` | Password for that account. Set a strong value on a cloud host. |
| `MYSQL_ROOT_PASSWORD` | `root-local` | `MYSQL_ROOT_PASSWORD`, health check | MySQL administrative password. Ledger does not use it. |
| `MYSQL_PORT` | `3306` | MySQL host port | Published only to `127.0.0.1`; use SSH tunneling for host administration. |
| `KAFKA_EXTERNAL_HOST` | `192.168.52.100` | Kafka advertised external listener | Public/static IP or DNS that local wallet clients use. This must be set explicitly on a cloud host. |
| `KAFKA_EXTERNAL_PORT` | `9092` | Kafka port mapping and advertised external listener | Use `19092` when local wallet-service is configured to reach `host:19092`. |

| Ledger container variable | Default in `application-docker.yml` | Resolved value in Compose | Purpose |
| --- | --- | --- | --- |
| `DB_HOST` | `mysql` | default | Docker DNS name of the MySQL service. |
| `DB_PORT` | `3306` | default | Internal MySQL port. |
| `DB_NAME` | `trade_ledgerservice` | default | Ledger schema name. |
| `DB_USERNAME` | `ledger` | `${LEDGER_DB_USERNAME:-ledger}` | JDBC username. |
| `DB_PASSWORD` | `ledger-local` | `${LEDGER_DB_PASSWORD:-ledger-local}` | JDBC password. |
| `REDIS_HOST` | `redis` | default | Docker DNS name of Redis. |
| `REDIS_PORT` | `6379` | default | Internal Redis port. |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | default | Kafka's internal listener for the ledger container. |

Do not change `DB_NAME` independently. The current initialization script, `standard/sql/ledger_service.sql`, explicitly creates `trade_ledgerservice`; changing the application database name without changing that SQL leaves the new database without tables.

The custom `RedissonRegister` builds Redisson clients from Spring's `spring.redis.*` properties. Therefore `REDIS_HOST=redis` applies to both Spring Redis and Redisson; no Docker-specific `redisson.yml` change is required.

## Docker Network And Startup Behavior

All services join `z-exchange-net`. Docker provides DNS entries matching the Compose service names, so `mysql`, `redis`, `kafka`, and `ledger-service` are stable addresses. Container IPs are intentionally not used.

Kafka has two listeners:

| Client | Address | Reason |
| --- | --- | --- |
| Ledger container | `kafka:29092` | Internal Docker DNS and port; no traffic leaves the bridge network. |
| Local wallet-service | `${KAFKA_EXTERNAL_HOST}:${KAFKA_EXTERNAL_PORT}` | Kafka metadata must advertise an address reachable from the local machine. |

`ledger-service` waits for healthy MySQL, Redis, and Kafka before starting. This prevents a normal cold start from failing simply because a dependency has not finished initialization. `restart: unless-stopped` restarts services after host/container failures.

`ledger-service-logs`, `mysql-data`, `redis-data`, `prometheus-data`, and `grafana-data` retain state across `docker compose down` and container recreation. `ledger-service-logs` stores `/app/logs/ledger-service` as a Docker-managed named volume, so no server-side log directory or `chown` setup is required. The schema SQL is run only when `mysql-data` is empty. Because that SQL begins by dropping the ledger database, never run `docker compose down -v` against data you need to keep.

## Deploy On A Cloud Docker Host

1. Install Docker Engine and the Compose plugin on the cloud server, then clone or upload this repository.

2. Create a server-local `.env` beside `docker-compose.yml`. Do not commit it:

```dotenv
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=replace-with-a-long-random-password
MYSQL_ROOT_PASSWORD=replace-with-a-different-long-random-password
KAFKA_EXTERNAL_HOST=ledger.example.com
KAFKA_EXTERNAL_PORT=19092
```

`KAFKA_EXTERNAL_HOST` must be a static public IP or DNS name that resolves from the local wallet machine. It must not be `localhost`, `kafka`, or a private Docker address.

3. Permit only required inbound traffic in the cloud firewall/security group:

| Port | Consumer | Restriction |
| --- | --- | --- |
| `19092` | Local wallet Kafka producer/consumer | Allow only the local wallet machine or VPN CIDR. |
| `9191` | Local wallet gRPC clients | Allow only the local wallet machine or VPN CIDR. |
| `8081` | Optional debugging/Prometheus access | Restrict to an operator/VPN network; Actuator metrics are not authenticated. |
| `6379` | Local wallet Redis client, only for this split deployment | Allow only the local wallet machine or VPN CIDR. |
| `3306` | Database administration | Not publicly exposed; Compose binds it to server loopback. |

For a production system, prefer deploying wallet-service in the same private network as Redis and Kafka, or use a VPN with TLS/authentication. The current Redis and Kafka Compose configuration is suitable for controlled development/performance testing, not unrestricted internet exposure.

4. Start the stack from the repository root:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f mysql kafka ledger-service
```

5. Verify ledger after startup:

```bash
curl http://127.0.0.1:8081/actuator/health
curl http://127.0.0.1:8081/actuator/prometheus
```

Prometheus is available on host port `9290`; Grafana is available on `3000`.

## Connect A Local Wallet-Service To Cloud Ledger

The local wallet database remains local. Start it with its normal local JDBC settings, but override the remote Redis, Kafka, and ledger gRPC endpoints. In PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "cloud"
$env:SPRING_REDIS_HOST = "ledger.example.com"
$env:SPRING_REDIS_PORT = "6379"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "ledger.example.com:19092"
$env:GRPC_CLIENT_LEDGER_CLIENT_ADDRESS = "static://ledger.example.com:9191"
$env:GRPC_CLIENT_ACCOUNT_CLIENT_ADDRESS = "static://ledger.example.com:9191"

mvn -pl wallet-service -am spring-boot:run
```

Use the actual public DNS/IP in place of `ledger.example.com`. Environment variables override the addresses in `wallet-service/src/main/resources/application-cloud.yml`, so that profile's currently committed IP does not need to be edited for each deployment.

The two gRPC client values are both required for `CreateWallet`: `CreateWalletProcessor` calls ledger's account gRPC service synchronously. Transaction posting uses Kafka instead, so `SPRING_KAFKA_BOOTSTRAP_SERVERS` must also be correct or wallet transactions will remain in the wallet outbox.

Send external test requests to the local wallet gRPC server at `127.0.0.1:9192`. The wallet server defines its gRPC port as `9192`; it is not Kafka's `9092`/`19092` port. The current `client-test` configuration points `wallet-client` to `127.0.0.1:9092`, which is incorrect for a local wallet gRPC request. Override it to `static://127.0.0.1:9192` before using that test client.

## Operational Checks

After a wallet transaction, inspect the asynchronous path in this order:

1. Wallet outbox has sent `wallet.ledger.command.posting`.
2. Ledger consumes that Kafka command and inserts ledger entries plus its reply outbox.
3. Ledger publishes `ledger.wallet.reply.posting`.
4. Local wallet consumes the reply and advances the wallet transaction.

If the message stalls, check the wallet and ledger outbox tables, Kafka topic/consumer lag, and `docker compose logs ledger-service kafka` before manually changing transaction state. The flow is intentionally at-least-once and must retain its outbox/idempotency behavior.
