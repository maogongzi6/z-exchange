# Ledger Split Docker Deployment

## Topology

This deployment uses two EC2 instances in one AWS VPC:

```text
ledger EC2 (172.31.16.37)             infrastructure EC2 (172.31.18.211)
---------------------                 -------------------------------
ledger-service:8081  <-- Prometheus -- Prometheus:9290
ledger-service:9191                   Grafana:3000
        |                              MySQL:3306
        +----------------------------> Redis:6379
        +----------------------------> Kafka:9092
        +----------------------------> MySQL:3306
```

The VPC security group is the network boundary. The Compose files bind ports to the supplied private IPs; no application dependency uses Docker DNS across EC2 instances.

## Deployment Files

| File | Host | Responsibility |
| --- | --- | --- |
| `deploy/docker-compose.infrastructure.yml` | `172.31.18.211` | MySQL, Redis, Kafka, Prometheus, Grafana, and persistent data volumes. |
| `deploy/docker-compose.ledger-service.yml` | `172.31.16.37` | Ledger application image and ledger log volume. |
| `ledger-service/src/main/resources/application-docker.yml` | Ledger image | Defaults for the infrastructure VPC endpoint; Compose environment variables can override them. |
| `monitoring/prometheus.yml` | Infrastructure host | Scrapes `172.31.16.37:8081/actuator/prometheus`. |

The root `docker-compose.yml` remains the legacy all-in-one configuration for local development. Do not use it for this split AWS deployment.

## Connection Configuration

The ledger Compose file provides these runtime values:

| Variable | Default | Used for |
| --- | --- | --- |
| `INFRA_PRIVATE_IP` | `172.31.18.211` | MySQL, Redis, and Kafka host. |
| `LEDGER_PRIVATE_IP` | `172.31.16.37` | Ledger HTTP and gRPC host bindings. |
| `LEDGER_DB_USERNAME` | `ledger` | MySQL application user. |
| `LEDGER_DB_PASSWORD` | `ledger-local` | MySQL application password. |
| `KAFKA_BOOTSTRAP_SERVERS` | `172.31.18.211:9092` | Kafka client bootstrap endpoint. |

Kafka advertises `172.31.18.211:9092` to VPC clients. This is required because Kafka clients use broker metadata after bootstrap; advertising `kafka:29092` would only work inside the infrastructure EC2's Docker network.

`application-docker.yml` uses the same defaults, so these environment variables are optional for the supplied IPs but should be set through server-local `.env` files to make the topology explicit.

## Infrastructure EC2

Use `deploy/infra-test.env`

```dotenv
INFRA_PRIVATE_IP=172.31.18.211
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=replace-with-a-long-random-password
MYSQL_ROOT_PASSWORD=replace-with-a-different-long-random-password
KAFKA_EXTERNAL_PORT=9092
```

Start the infrastructure services from the repository root:

```bash
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml up -d
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml ps
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml logs -f mysql redis kafka prometheus
```

MySQL initialization files run only when the `mysql-data` volume is empty. The mounted `001-ledger-service.sql` creates the ledger schema and tables. It does not run on restarts or when the existing data volume is reused.

## Ledger EC2

Use `deploy/ledger-test.env`. `LEDGER_DB_PASSWORD` must match the value used on the infrastructure EC2:

```dotenv
LEDGER_PRIVATE_IP=172.31.16.37
INFRA_PRIVATE_IP=172.31.18.211
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=replace-with-the-infrastructure-value
KAFKA_BOOTSTRAP_SERVERS=172.31.18.211:9092
```

Build and start ledger-service from the repository root:

```bash
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml up --build -d
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml ps
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml logs -f ledger-service
```

Verify from the ledger EC2:

```bash
curl http://172.31.16.37:8081/actuator/health
curl http://172.31.16.37:8081/actuator/prometheus
```

## Monitoring

Prometheus runs on the infrastructure EC2 and pulls the ledger metric endpoint through the VPC:

```text
Prometheus (172.31.18.211) -> ledger-service (172.31.16.37:8081)
```

Verify the scrape target in Prometheus at `http://172.31.18.211:9290/targets`. Grafana is available at `http://172.31.18.211:3000`.

Permit the following security-group traffic within the VPC: infrastructure EC2 to ledger EC2 TCP `8081`; ledger EC2 to infrastructure EC2 TCP `3306`, `6379`, and `9092`; and approved operator networks to Grafana TCP `3000` and Prometheus TCP `9290`. Ledger gRPC TCP `9191` should be allowed only from service clients that need it.
