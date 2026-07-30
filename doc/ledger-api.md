# Ledger API Boundaries

## Purpose

Ledger exposes two transports over the same application processors:

- gRPC is the internal service-to-service contract.
- HTTP provides read-only, RESTful transaction queries for external clients and
  HTTP-based performance tests.

The HTTP controller does not call the gRPC service implementation. Both
adapters call `GetLedgerTxnProcessor`, which keeps caching, database access, and
business behavior identical across transports.

## HTTP API

The HTTP server uses ledger-service port `8081`.

### Get by ledger transaction ID

```http
GET /api/v1/ledger/transactions/{ledgerTxnId}?includeEntries=true
```

The ledger transaction ID is the canonical resource identifier.
`includeEntries` defaults to `false`; enabling it expands the response with the
double-entry records.

### Get by reference ID

```http
GET /api/v1/ledger/transactions?referenceId={referenceId}&includeEntries=true
```

Reference ID is a unique alternate lookup key, represented as a collection
filter rather than an action endpoint. It returns the matching transaction or
`404 Not Found`.

Successful responses contain:

```json
{
  "transaction": {
    "ledgerTxnId": "ledger-txn-id",
    "referenceId": "wallet-reference-id",
    "metadata": "{}"
  },
  "entries": [
    {
      "ledgerEntryId": "entry-id",
      "accountId": "account-id",
      "direction": "DEBIT",
      "amount": 100,
      "assetId": "asset-1"
    }
  ]
}
```

Error responses use the ledger namespace and an HTTP status appropriate to the
error category:

```json
{
  "error": {
    "namespace": "ledger",
    "code": 1001,
    "message": "ledger_not_found",
    "detail": "ledger not found"
  }
}
```

Internal error details are not returned to external clients.

## gRPC API

The gRPC server uses port `9191`.

- `PostService/postTransaction` is the internal write command. It remains gRPC
  only because wallet-to-ledger writes are internal asynchronous choreography,
  not a public ledger operation.
- `PostService/getTxnById` is the internal query by canonical ledger ID.
- `PostService/getTxnByRefId` is the internal query by the originating business
  reference.
- `AccountService/createAccount` is internal setup used by wallet integration
  and smoke-test fixtures.

Keeping internal gRPC avoids JSON conversion and provides generated protobuf
contracts between trusted services. Keeping public HTTP read-only prevents
external callers from bypassing wallet, which owns transaction decisions and
balance constraints.

## Metrics

Both HTTP query methods call the processor through
`LedgerBusinessMetrics.recordLedgerTxnQuery`. Existing
`zexchange.ledger.requests` and `zexchange.ledger.processing.duration` metrics
therefore continue to record the same operation labels, with `ingress=http`.
The gRPC path records `ingress=grpc`.

Spring also records HTTP transport metrics independently. Compare HTTP
transport duration with ledger processing duration when determining whether
the HTTP adapter, rather than the processor or data stores, is saturated.

## Exposure and Security

The current deployment binds HTTP to the ledger EC2 private VPC address. Do not
expose port `8081` directly to the public internet. External access should pass
through an API gateway or equivalent authentication and authorization layer.
Authorization must verify that the caller owns or may view the requested
transaction; knowledge of a transaction or reference ID is not authorization.

## Smoke Tests

- `LedgerPostServiceSmokeSimulation` verifies all three `PostService` gRPC
  methods with one virtual user.
- `LedgerHttpQuerySmokeSimulation` creates a fixture through internal gRPC
  before measurement, then verifies both HTTP query forms and their JSON
  response shape.

The HTTP smoke is a correctness check. Longer HTTP workloads can measure the
read path without Gatling Community's gRPC-specific five-user/five-minute
limit, but their throughput must be reported as HTTP capacity rather than gRPC
capacity.
