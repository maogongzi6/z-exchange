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
- `AccountService/createAccount` is internal account setup used by wallet
  integration.

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

`stress-test/scripts/ledger-grpc-smoke.js` uses k6 to verify all three
`PostService` methods with one virtual user and one iteration:

1. It posts a balanced transaction.
2. It retrieves that transaction by reference ID.
3. It retrieves the same transaction by ledger transaction ID.

The stress-test entrypoint inserts the dedicated `k6-smoke-asset` Asset and
`k6-smoke-account` Account directly into the ledger MySQL database before k6
starts. Fixture time is therefore excluded from gRPC measurements. The SQL uses
idempotent upserts, while the posted transaction receives a unique reference ID
on every run.

k6 loads the same source protobuf definitions used by ledger-service. It reuses
one HTTP/2 connection for all three calls, checks both gRPC status and protobuf
business error values, and exits nonzero if any correctness check or latency
threshold fails.

HTTP remains useful for external read clients, but ledger gRPC capacity must be
measured through the gRPC contract rather than through the HTTP adapter. Future
throughput tests should extend the k6 gRPC workload with an open arrival-rate
executor instead of treating HTTP capacity as a proxy.
