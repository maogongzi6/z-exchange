FROM grafana/k6:2.1.0 AS k6

FROM golang:1.24-alpine AS cache-reset

COPY stress-test/tools/wallet-cache-reset.go /src/wallet-cache-reset.go
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" \
    -o /wallet-cache-reset /src/wallet-cache-reset.go

FROM mysql:8.4

# Use MySQL's native client for fixture authentication and copy the standalone
# k6 binary into the same short-lived runner image.
COPY --from=k6 /usr/bin/k6 /usr/bin/k6
COPY --from=cache-reset /wallet-cache-reset /usr/bin/wallet-cache-reset

COPY stress-test/scripts/ /scripts/
COPY stress-test/fixtures/ /fixtures/
COPY stress-test/run-smoke.sh /run-smoke.sh
COPY stress-test/run-wallet-smoke.sh /run-wallet-smoke.sh
COPY stress-test/run-write-stress.sh /run-write-stress.sh
COPY stress-test/run-query-stress.sh /run-query-stress.sh
COPY stress-test/run-wallet-write-stress.sh /run-wallet-write-stress.sh
COPY stress-test/run-wallet-query-stress.sh /run-wallet-query-stress.sh

# Preserve the source proto directory structure so ledger and wallet imports
# resolve exactly as they do in the Java build.
COPY grpc-proto/common-proto/src/main/proto/ /proto/
COPY grpc-proto/ledger-proto/src/main/proto/ /proto/
COPY grpc-proto/wallet-proto/src/main/proto/ /proto/

RUN chmod +x /run-smoke.sh /run-wallet-smoke.sh /run-write-stress.sh \
    /run-query-stress.sh /run-wallet-write-stress.sh /run-wallet-query-stress.sh

ENTRYPOINT ["/run-smoke.sh"]
CMD []
