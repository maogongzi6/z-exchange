FROM maven:3.9.11-eclipse-temurin-21

WORKDIR /workspace

# Build from the repository root because the test module consumes ledger-proto
# from the same Maven reactor. The install also primes Maven's local repository
# so the runtime invocation can be completely offline.
COPY . .
RUN mvn -pl stress-test -am -DskipTests install \
    && mvn -pl stress-test -DskipTests gatling:help \
    && chmod +x stress-test/run-smoke.sh

ENTRYPOINT ["/workspace/stress-test/run-smoke.sh"]
