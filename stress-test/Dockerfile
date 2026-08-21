FROM grafana/k6:2.1.0 AS k6

FROM mysql:8.4

# Use MySQL's native client for fixture authentication and copy the standalone
# k6 binary into the same short-lived runner image.
COPY --from=k6 /usr/bin/k6 /usr/bin/k6

COPY stress-test/scripts/ /scripts/
COPY stress-test/fixtures/ /fixtures/
COPY stress-test/run-smoke.sh /run-smoke.sh
COPY stress-test/run-wallet-smoke.sh /run-wallet-smoke.sh
COPY stress-test/run-write-stress.sh /run-write-stress.sh
COPY stress-test/run-query-stress.sh /run-query-stress.sh

# Preserve the source proto directory structure so ledger and wallet imports
# resolve exactly as they do in the Java build.
COPY grpc-proto/common-proto/src/main/proto/ /proto/
COPY grpc-proto/ledger-proto/src/main/proto/ /proto/
COPY grpc-proto/wallet-proto/src/main/proto/ /proto/

RUN chmod +x /run-smoke.sh /run-wallet-smoke.sh /run-write-stress.sh /run-query-stress.sh

ENTRYPOINT ["/run-smoke.sh"]
CMD []
