# Ledger Split Docker Deployment

## Topology

This deployment uses three EC2 instances in one AWS VPC:

```text
ledger EC2                    infrastructure EC2           monitoring EC2
172.31.16.37                 172.31.18.211                172.31.28.170
--------------------          ---------------------         ------------------
ledger-service:8081 <-------------------------------------- Prometheus:9290
ledger-service:9191           MySQL:3306                    Grafana:3000
        |                     Redis:6379                         |
        +-------------------> Kafka:9092                         +-> Prometheus:9090
        +-------------------> MySQL:3306
```

The VPC security groups are the network boundary. Compose binds published ports
to each host's private IP; Docker DNS is used only between containers on the
same EC2 instance.

## Deployment Files

| File | Host | Responsibility |
| --- | --- | --- |
| `deploy/docker-compose.infrastructure.yml` | `172.31.18.211` | MySQL, Redis, Kafka, and MySQL data. |
| `deploy/docker-compose.ledger-service.yml` | `172.31.16.37` | Ledger application and ledger logs. |
| `deploy/docker-compose.monitoring.yml` | `172.31.28.170` | Prometheus, Grafana, and monitoring data. |
| `deploy/infra-test.env` | Infrastructure host | Infrastructure IP and database credentials. |
| `deploy/ledger-test.env` | Ledger host | Ledger and infrastructure endpoints. |
| `deploy/monitor-test.env` | Monitoring host | Monitoring IP and container memory budgets. |
| `ledger-service/src/main/resources/application-docker.yml` | Ledger image | Docker profile defaults, overridable by Compose. |
| `monitoring/prometheus.yml` | Monitoring host | Scrapes `172.31.16.37:8081/actuator/prometheus`. |

The root `docker-compose.yml` remains the legacy all-in-one configuration for
local development. Do not use it for this AWS deployment.

## Connection Configuration

| Variable | Default | Used for |
| --- | --- | --- |
| `INFRA_PRIVATE_IP` | `172.31.18.211` | MySQL, Redis, and Kafka host. |
| `LEDGER_PRIVATE_IP` | `172.31.16.37` | Ledger HTTP and gRPC bindings. |
| `MONITOR_PRIVATE_IP` | `172.31.28.170` | Prometheus and Grafana bindings. |
| `LEDGER_DB_USERNAME` | `ledger` | MySQL application user. |
| `LEDGER_DB_PASSWORD` | `ledger-local` | MySQL application password. |
| `KAFKA_BOOTSTRAP_SERVERS` | `172.31.18.211:9092` | Kafka client bootstrap endpoint. |
| `K6_PROMETHEUS_RW_SERVER_URL` | `http://172.31.28.170:9290/api/v1/write` | k6 metric destination. |

Kafka advertises `172.31.18.211:9092` because VPC clients cannot resolve its
Docker-only `kafka:29092` listener.

## Infrastructure EC2

Use `deploy/infra-test.env`:

```dotenv
INFRA_PRIVATE_IP=172.31.18.211
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=replace-with-a-long-random-password
MYSQL_ROOT_PASSWORD=replace-with-a-different-long-random-password
KAFKA_EXTERNAL_PORT=9092
MYSQL_MEMORY_LIMIT=1792m
MYSQL_MEMORY_RESERVATION=1280m
```

Start the infrastructure services from the repository root:

```bash
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml up -d
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml ps
docker compose --env-file deploy/infra-test.env -f deploy/docker-compose.infrastructure.yml logs -f mysql redis kafka
```

MySQL initialization runs only while creating an empty `mysql-data` volume.
The mounted `001-ledger-service.sql` does not run on ordinary restarts.

## Ledger EC2

Use `deploy/ledger-test.env`. Its database password must match the infrastructure
host:

```dotenv
LEDGER_PRIVATE_IP=172.31.16.37
INFRA_PRIVATE_IP=172.31.18.211
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=replace-with-the-infrastructure-value
KAFKA_BOOTSTRAP_SERVERS=172.31.18.211:9092
```

Build and start ledger-service:

```bash
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml up --build -d
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml ps
docker compose --env-file deploy/ledger-test.env -f deploy/docker-compose.ledger-service.yml logs -f ledger-service
```

Verify the service:

```bash
curl http://172.31.16.37:8081/actuator/health
curl http://172.31.16.37:8081/actuator/prometheus
```

## Monitoring EC2

`deploy/monitor-test.env` configures the 2 GiB monitoring host:

```dotenv
MONITOR_PRIVATE_IP=172.31.28.170
PROMETHEUS_MEMORY_LIMIT=512m
PROMETHEUS_MEMORY_RESERVATION=384m
GRAFANA_MEMORY_LIMIT=768m
GRAFANA_MEMORY_RESERVATION=512m
```

Start Prometheus and Grafana:

```bash
docker compose --env-file deploy/monitor-test.env -f deploy/docker-compose.monitoring.yml up -d
docker compose --env-file deploy/monitor-test.env -f deploy/docker-compose.monitoring.yml ps
docker compose --env-file deploy/monitor-test.env -f deploy/docker-compose.monitoring.yml logs -f prometheus grafana
```

Verify:

```text
Prometheus targets: http://172.31.28.170:9290/targets
Grafana:            http://172.31.28.170:3000
```

Grafana reaches Prometheus through `http://prometheus:9090` on their shared
Docker network. Prometheus scrapes ledger-service through the VPC. k6 sends
remote-write data to `http://172.31.28.170:9290/api/v1/write`.

The named Prometheus and Grafana volumes are local to the monitoring EC2. A
fresh deployment does not copy history from `172.31.18.211`. Dashboard JSON is
reprovisioned from the repository; migrate or snapshot the old Prometheus volume
separately only when its history must be retained.

After the new targets and dashboards are verified, stop and remove the old
monitoring containers on the infrastructure EC2. This leaves their volumes
intact for rollback:

```bash
docker stop prometheus grafana
docker rm prometheus grafana
```

## Stress-Test Endpoint

The stress-test Compose files default to the new monitoring host. An explicit
override remains supported:

```bash
export K6_PROMETHEUS_RW_SERVER_URL=http://172.31.28.170:9290/api/v1/write
```

Database fixture and reconciliation traffic still uses
`INFRA_PRIVATE_IP=172.31.18.211`; gRPC traffic still uses
`LEDGER_GRPC_HOST=172.31.16.37`.

## Security Groups

Permit only the required flows:

| Source | Destination | TCP port | Purpose |
| --- | --- | ---: | --- |
| Ledger EC2 | Infrastructure EC2 | `3306`, `6379`, `9092` | MySQL, Redis, Kafka. |
| Monitoring EC2 | Ledger EC2 | `8081` | Prometheus scrape. |
| Stress-test EC2 | Ledger EC2 | `9191` | gRPC load. |
| Stress-test EC2 | Infrastructure EC2 | `3306` | Fixture and reconciliation SQL. |
| Stress-test EC2 | Monitoring EC2 | `9290` | k6 remote write. |
| Approved operator network | Monitoring EC2 | `3000` | Grafana UI. |
| Approved operator network, optional | Monitoring EC2 | `9290` | Prometheus UI/API. |

Do not expose MySQL, Redis, Kafka, Prometheus, Grafana, or the ledger actuator
to the public internet.
