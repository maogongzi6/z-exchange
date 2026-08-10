import grpc from 'k6/net/grpc';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const client = new grpc.Client();
client.load(['/proto'], 'com/exchange/proto/ledger/post_service.proto');

const PROFILE_RPC_DISCOVERY = 'rpc-discovery';
const PROFILE_WORKING_SET_DISCOVERY = 'working-set-discovery';
const PROFILE_SOAK = 'soak';
const LOOKUP_TXN_ID = 'txn-id';
const LOOKUP_REF_ID = 'ref-id';
const MISS_REPEATED = 'repeated';
const MISS_COLD = 'cold';

let connected = false;
let loggedFailureCount = 0;

// These metrics separate offered load, valid hits, expected misses, and real
// failures. A configured arrival rate alone does not prove completion.
const offeredQueries = new Counter('ledger_query_offered');
const completedQueries = new Counter('ledger_query_completed');
const successfulHits = new Counter('ledger_query_hit_successful');
const expectedMisses = new Counter('ledger_query_expected_not_found');
const unexpectedFailures = new Counter('ledger_query_unexpected_failures');
const connectionFailures = new Counter('ledger_query_connection_failures');
const coldMissReuses = new Counter('ledger_query_cold_miss_reuse');
const unexpectedErrorRate = new Rate('ledger_query_unexpected_error_rate');
const requestDuration = new Trend('ledger_query_request_duration', true);
const hitDuration = new Trend('ledger_query_hit_duration', true);
const missDuration = new Trend('ledger_query_miss_duration', true);

function integerEnv(name, fallback, minimum = 1) {
    const raw = __ENV[name];
    const value = raw === undefined || raw === '' ? fallback : Number(raw);
    if (!Number.isSafeInteger(value) || value < minimum) {
        throw new Error(`${name} must be an integer >= ${minimum}`);
    }
    return value;
}

function percentageEnv(name, fallback) {
    const value = integerEnv(name, fallback, 0);
    if (value > 100) {
        throw new Error(`${name} must be an integer between 0 and 100`);
    }
    return value;
}

function optionalRateEnv(name) {
    const raw = __ENV[name];
    if (raw === undefined || raw === '') {
        return null;
    }
    const value = Number(raw);
    if (!Number.isFinite(value) || value <= 0 || value >= 1) {
        throw new Error(`${name} must be greater than 0 and less than 1`);
    }
    return value;
}

function seconds(value) {
    return `${value}s`;
}

const ledgerHost = __ENV.LEDGER_GRPC_HOST || '172.31.16.37';
const ledgerPort = __ENV.LEDGER_GRPC_PORT || '9191';
const deadlineSeconds = integerEnv('LEDGER_GRPC_DEADLINE_SECONDS', 5);
const profile = (__ENV.LEDGER_QUERY_PROFILE || PROFILE_RPC_DISCOVERY).toLowerCase();
const lookup = (__ENV.LEDGER_QUERY_LOOKUP || LOOKUP_TXN_ID).toLowerCase();
const runId = __ENV.LEDGER_QUERY_RUN_ID;
const fixtureSize = integerEnv('LEDGER_QUERY_FIXTURE_SIZE', 10000);
const workingSetSize = integerEnv('LEDGER_QUERY_WORKING_SET_SIZE', 1000);
const workingSetStart = integerEnv('LEDGER_QUERY_WORKING_SET_START', 100);
const workingSetStep = integerEnv('LEDGER_QUERY_WORKING_SET_STEP', 100);
const workingSetSteps = integerEnv('LEDGER_QUERY_WORKING_SET_STEPS', 8);
const stageSeconds = integerEnv('LEDGER_QUERY_STAGE_SECONDS', 30);
const warmRate = integerEnv('LEDGER_QUERY_WARM_RATE', 5);
const queryRate = integerEnv('LEDGER_QUERY_RATE', 100);
const rateStep = integerEnv('LEDGER_QUERY_RATE_STEP', 25);
const rateSteps = integerEnv('LEDGER_QUERY_RATE_STEPS', 8);
const soakSeconds = integerEnv('LEDGER_QUERY_SOAK_SECONDS', 120);
const preAllocatedVUs = integerEnv('LEDGER_QUERY_PREALLOCATED_VUS', 100);
const maxVUs = integerEnv('LEDGER_QUERY_MAX_VUS', 500);
const missRatePercent = percentageEnv('LEDGER_QUERY_MISS_RATE_PERCENT', 0);
const missCardinalityPercent = percentageEnv(
    'LEDGER_QUERY_MISS_CARDINALITY_PERCENT',
    10,
);
const missPattern = (__ENV.LEDGER_QUERY_MISS_PATTERN || MISS_REPEATED).toLowerCase();

if (!runId || !/^[A-Za-z0-9-]{1,24}$/.test(runId)) {
    throw new Error(
        'LEDGER_QUERY_RUN_ID is required and must contain 1-24 letters, digits, or hyphens',
    );
}
if (![PROFILE_RPC_DISCOVERY, PROFILE_WORKING_SET_DISCOVERY, PROFILE_SOAK].includes(profile)) {
    throw new Error(
        'LEDGER_QUERY_PROFILE must be rpc-discovery, working-set-discovery, or soak',
    );
}
if (![LOOKUP_TXN_ID, LOOKUP_REF_ID].includes(lookup)) {
    throw new Error('LEDGER_QUERY_LOOKUP must be txn-id or ref-id');
}
if (![MISS_REPEATED, MISS_COLD].includes(missPattern)) {
    throw new Error('LEDGER_QUERY_MISS_PATTERN must be repeated or cold');
}
if (missRatePercent > 0 && missCardinalityPercent === 0) {
    throw new Error(
        'LEDGER_QUERY_MISS_CARDINALITY_PERCENT must be greater than 0 when misses are enabled',
    );
}
if (maxVUs < preAllocatedVUs) {
    throw new Error('LEDGER_QUERY_MAX_VUS must be >= LEDGER_QUERY_PREALLOCATED_VUS');
}

const largestWorkingSet = profile === PROFILE_WORKING_SET_DISCOVERY
    ? workingSetStart + workingSetStep * workingSetSteps
    : workingSetSize;
if (largestWorkingSet > fixtureSize) {
    throw new Error('the largest configured working set exceeds LEDGER_QUERY_FIXTURE_SIZE');
}

const operation = lookup === LOOKUP_TXN_ID ? 'getTxnById' : 'getTxnByRefId';
const grpcMethod = `ledger.PostService/${operation}`;
const metricTags = { operation };

function rpcDiscoveryStages() {
    const stages = [{ duration: seconds(stageSeconds), target: warmRate }];
    for (let step = 1; step <= rateSteps; step += 1) {
        const target = warmRate + rateStep * step;
        // Move to the next load step quickly, then hold it long enough to make
        // the latency/throughput response visible on the dashboard.
        stages.push({ duration: '1s', target });
        stages.push({ duration: seconds(stageSeconds), target });
    }
    stages.push({ duration: '1s', target: 0 });
    return stages;
}

function scenarioOptions() {
    if (profile === PROFILE_WORKING_SET_DISCOVERY) {
        return {
            ledgerQuery: {
                executor: 'constant-arrival-rate',
                exec: 'ledgerQuery',
                rate: queryRate,
                timeUnit: '1s',
                duration: seconds(stageSeconds * (workingSetSteps + 1)),
                preAllocatedVUs,
                maxVUs,
                gracefulStop: seconds(deadlineSeconds * 2 + 5),
            },
        };
    }

    const stages = profile === PROFILE_RPC_DISCOVERY
        ? rpcDiscoveryStages()
        : [
            { duration: seconds(stageSeconds), target: warmRate },
            { duration: '1s', target: queryRate },
            { duration: seconds(soakSeconds), target: queryRate },
            { duration: '1s', target: 0 },
        ];
    return {
        ledgerQuery: {
            executor: 'ramping-arrival-rate',
            exec: 'ledgerQuery',
            startRate: warmRate,
            timeUnit: '1s',
            stages,
            preAllocatedVUs,
            maxVUs,
            gracefulStop: seconds(deadlineSeconds * 2 + 5),
        },
    };
}

const thresholds = {};
const maxUnexpectedErrorRate = optionalRateEnv('LEDGER_QUERY_MAX_UNEXPECTED_ERROR_RATE');
if (maxUnexpectedErrorRate !== null) {
    thresholds.ledger_query_unexpected_error_rate = [
        `rate<${maxUnexpectedErrorRate}`,
    ];
}
const maxP95Ms = __ENV.LEDGER_QUERY_MAX_P95_MS
    ? integerEnv('LEDGER_QUERY_MAX_P95_MS', 0)
    : null;
if (maxP95Ms !== null) {
    thresholds[`ledger_query_request_duration{operation:${operation}}`] = [
        `p(95)<${maxP95Ms}`,
    ];
}
if (missRatePercent > 0 && missPattern === MISS_COLD) {
    // A cold-miss run is no longer cold after its fixed missing-key pool wraps.
    // Fail visibly instead of silently turning it into a repeated-miss test.
    thresholds.ledger_query_cold_miss_reuse = ['count==0'];
}

export const options = {
    scenarios: scenarioOptions(),
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    tags: {
        testid: runId,
        test_type: 'ledger_query',
        test_profile: profile,
        lookup,
        miss_pattern: missPattern,
    },
};

export function setup() {
    // setup() runs once, unlike top-level module code which is evaluated for
    // every VU. Keep the effective test configuration visible without noisy
    // duplicate logging.
    console.log(JSON.stringify({
        profile,
        lookup,
        fixtureSize,
        largestWorkingSet,
        missRatePercent,
        missCardinalityPercent,
        missPattern,
    }));
}

function currentWorkingSetSize() {
    if (profile !== PROFILE_WORKING_SET_DISCOVERY) {
        return workingSetSize;
    }

    // k6 exposes the scenario start time consistently across VUs. Deriving the
    // step from elapsed time changes only the working set while RPC stays fixed.
    const elapsedSeconds = Math.max(
        0,
        (Date.now() - Number(exec.scenario.startTime)) / 1000,
    );
    const step = Math.min(
        workingSetSteps,
        Math.floor(elapsedSeconds / stageSeconds),
    );
    return workingSetStart + workingSetStep * step;
}

function paddedIndex(index) {
    return String(index).padStart(6, '0');
}

function hitLookupValue(index) {
    const suffix = paddedIndex(index);
    return lookup === LOOKUP_TXN_ID
        ? `k6-query-txn-${suffix}`
        : `k6-query-ref-${suffix}`;
}

function missOrdinalFor(sequence) {
    // The first missRatePercent positions of each 100-iteration block are
    // misses, giving a deterministic whole-number miss rate.
    return Math.floor(sequence / 100) * missRatePercent + (sequence % 100);
}

function missLookupValue(sequence, currentWorkingSet) {
    const missSetSize = Math.max(
        1,
        Math.ceil(currentWorkingSet * missCardinalityPercent / 100),
    );
    const missOrdinal = missOrdinalFor(sequence);
    if (missPattern === MISS_COLD && missOrdinal >= missSetSize) {
        coldMissReuses.add(1, metricTags);
    }

    // Repeated mode intentionally cycles this bounded set. Cold mode uses each
    // member once; the threshold above rejects a run that reaches a second pass.
    const missIndex = missOrdinal % missSetSize;
    const type = lookup === LOOKUP_TXN_ID ? 'txn' : 'ref';
    return `k6-query-miss:${runId}:${type}:${paddedIndex(missIndex)}`;
}

function businessError(response) {
    return response && response.message && response.message.error;
}

function isBusinessSuccess(response) {
    const error = businessError(response);
    return !error
        || error.code === undefined
        || error.code === 0
        || error.code === 'ERROR_OK';
}

function isNotFound(response) {
    const error = businessError(response);
    return Boolean(error)
        && (error.code === 3000 || error.code === 'ERROR_NOT_FOUND');
}

function validHit(response, expectedLookup) {
    if (!response || response.status !== grpc.StatusOK || !isBusinessSuccess(response)) {
        return false;
    }
    const message = response.message || {};
    const transaction = message.transaction || {};
    const lookupMatches = lookup === LOOKUP_TXN_ID
        ? transaction.ledgerTxnId === expectedLookup
        : transaction.referenceId === expectedLookup;
    return lookupMatches && message.entries && message.entries.length === 2;
}

function logFailureSample(classification, responseOrError, expectedLookup) {
    // Unlimited logs can bottleneck the generator during failure bursts and
    // distort the test. Keep only a few diagnostic samples per VU.
    if (loggedFailureCount >= 3) {
        return;
    }
    loggedFailureCount += 1;
    console.error(JSON.stringify({ classification, expectedLookup, responseOrError }));
}

export async function ledgerQuery() {
    if (!connected) {
        try {
            client.connect(`${ledgerHost}:${ledgerPort}`, {
                plaintext: true,
                timeout: `${deadlineSeconds}s`,
            });
            connected = true;
        } catch (error) {
            connectionFailures.add(1, metricTags);
            unexpectedFailures.add(1, { ...metricTags, outcome: 'connection_failure' });
            unexpectedErrorRate.add(true, { ...metricTags, outcome: 'connection_failure' });
            logFailureSample('connection_failure', String(error), null);
            check(null, { 'ledger gRPC connection established': () => false }, metricTags);
            return;
        }
    }

    const sequence = exec.scenario.iterationInTest;
    const currentWorkingSet = currentWorkingSetSize();
    const expectedMiss = sequence % 100 < missRatePercent;
    const lookupValue = expectedMiss
        ? missLookupValue(sequence, currentWorkingSet)
        : hitLookupValue(sequence % currentWorkingSet);
    const request = lookup === LOOKUP_TXN_ID
        ? { ledgerTxnId: lookupValue, includeEntries: true }
        : { referenceId: lookupValue, includeEntries: true };

    offeredQueries.add(1, metricTags);
    const startedAt = Date.now();
    try {
        const response = await client.asyncInvoke(grpcMethod, request, {
            timeout: `${deadlineSeconds}s`,
            tags: metricTags,
        });
        const elapsedMs = Date.now() - startedAt;
        completedQueries.add(1, metricTags);
        requestDuration.add(elapsedMs, metricTags);

        let classification;
        let unexpected;
        if (expectedMiss && response && response.status === grpc.StatusOK && isNotFound(response)) {
            classification = 'expected_not_found';
            unexpected = false;
            expectedMisses.add(1, metricTags);
            missDuration.add(elapsedMs, metricTags);
        } else if (!expectedMiss && validHit(response, lookupValue)) {
            classification = 'hit_success';
            unexpected = false;
            successfulHits.add(1, metricTags);
            hitDuration.add(elapsedMs, metricTags);
        } else {
            classification = !response || response.status !== grpc.StatusOK
                ? 'transport_error'
                : 'unexpected_business_result';
            unexpected = true;
            unexpectedFailures.add(1, { ...metricTags, outcome: classification });
            logFailureSample(classification, response, lookupValue);
        }

        const resultTags = { ...metricTags, outcome: classification };
        unexpectedErrorRate.add(unexpected, resultTags);
        check(response, {
            'ledger query returned the expected outcome': () => !unexpected,
        }, resultTags);
    } catch (error) {
        const elapsedMs = Date.now() - startedAt;
        const resultTags = { ...metricTags, outcome: 'transport_exception' };
        requestDuration.add(elapsedMs, metricTags);
        unexpectedFailures.add(1, resultTags);
        unexpectedErrorRate.add(true, resultTags);
        logFailureSample('transport_exception', String(error), lookupValue);
        check(null, {
            'ledger query returned the expected outcome': () => false,
        }, resultTags);
    }
}
