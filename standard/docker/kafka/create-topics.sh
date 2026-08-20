#!/bin/bash
set -euo pipefail

bootstrap_server="${KAFKA_BOOTSTRAP_SERVER:-kafka:29092}"
partitions="${KAFKA_TOPIC_PARTITIONS:-3}"
replication_factor="${KAFKA_TOPIC_REPLICATION_FACTOR:-1}"
retry_topic_count="${KAFKA_RETRY_TOPIC_COUNT:-6}"

if ! [[ "${retry_topic_count}" =~ ^[0-9]+$ ]]; then
    echo "KAFKA_RETRY_TOPIC_COUNT must be a non-negative integer" >&2
    exit 1
fi

create_topic() {
    local topic="$1"
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "${bootstrap_server}" \
        --create \
        --if-not-exists \
        --topic "${topic}" \
        --partitions "${partitions}" \
        --replication-factor "${replication_factor}"
}

IFS=',' read -ra base_topics <<< "${KAFKA_BASE_TOPICS:-wallet.ledger.command.posting,ledger.wallet.reply.posting}"
for raw_topic in "${base_topics[@]}"; do
    topic="${raw_topic//[[:space:]]/}"
    if [[ -z "${topic}" ]]; then
        continue
    fi

    create_topic "${topic}"
    for ((index = 0; index < retry_topic_count; index++)); do
        create_topic "${topic}.retry-${index}"
    done
    create_topic "${topic}.dlq"
done

echo "Kafka topics are ready:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${bootstrap_server}" --list
