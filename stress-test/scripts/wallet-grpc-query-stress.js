import grpc from 'k6/net/grpc';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const client = new grpc.Client();
client.load(['/proto'], 'wallet/wallet_service.proto');

const PROFILE_RPC_DISCOVERY = 'rpc-discovery';
const PROFILE_WORKING_SET_DISCOVERY = 'working-set-discovery';
const PROFILE_SOAK = 'soak';
const LOOKUP_WALLET_ID = 'wallet-id';
const LOOKUP_REF_ID = 'ref-id';
const MISS_REPEATED = 'repeated';
const MISS_COLD = 'cold';

let connected = false;
let loggedFailureCount = 0;

const offeredQueries = new Counter('wallet_query_offered');
const completedQueries = new Counter('wallet_query_completed');
const successfulHits = new Counter('wallet_query_successful_hits');
const expectedMisses = new Counter('wallet_query_expected_misses');
const unexpectedFailures = new Counter('wallet_query_unexpected_failures');
const connectionFailures = new Counter('wallet_query_connection_failures');
const coldMissReuses = new Counter('wallet_query_cold_miss_reuse');
const unexpectedErrorRate = new Rate('wallet_query_unexpected_error_rate');
const requestDuration = new Trend('wallet_query_request_duration', true);
const hitDuration = new Trend('wallet_query_hit_duration', true);
const missDuration = new Trend('wallet_query_miss_duration', true);

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
    if (raw === undefined || raw === '') return null;
    const value = Number(raw);
    if (!Number.isFinite(value) || value <= 0 || value >= 1) {
        throw new Error(`${name} must be greater than 0 and less than 1`);
    }
    return value;
}

const walletHost = __ENV.WALLET_GRPC_HOST || '172.31.23.255';
const walletPort = __ENV.WALLET_GRPC_PORT || '9192';
const deadlineSeconds = integerEnv('WALLET_GRPC_DEADLINE_SECONDS', 5);
const profile = (__ENV.WALLET_QUERY_PROFILE || PROFILE_RPC_DISCOVERY).toLowerCase();
const lookup = (__ENV.WALLET_QUERY_LOOKUP || LOOKUP_WALLET_ID).toLowerCase();
const runId = __ENV.WALLET_QUERY_RUN_ID;
const fixtureSize = integerEnv('WALLET_QUERY_FIXTURE_SIZE', 10000);
const workingSetSize = integerEnv('WALLET_QUERY_WORKING_SET_SIZE', 1000);
const workingSetStart = integerEnv('WALLET_QUERY_WORKING_SET_START', 100);
const workingSetStep = integerEnv('WALLET_QUERY_WORKING_SET_STEP', 100);
const workingSetSteps = integerEnv('WALLET_QUERY_WORKING_SET_STEPS', 8);
const stageSeconds = integerEnv('WALLET_QUERY_STAGE_SECONDS', 30);
const warmRate = integerEnv('WALLET_QUERY_WARM_RATE', 5);
const queryRate = integerEnv('WALLET_QUERY_RATE', 100);
const rateStep = integerEnv('WALLET_QUERY_RATE_STEP', 25);
const rateSteps = integerEnv('WALLET_QUERY_RATE_STEPS', 8);
const soakSeconds = integerEnv('WALLET_QUERY_SOAK_SECONDS', 1800);
const preAllocatedVUs = integerEnv('WALLET_QUERY_PREALLOCATED_VUS', 100);
const maxVUs = integerEnv('WALLET_QUERY_MAX_VUS', 500);
const hitRatePercent = percentageEnv('WALLET_QUERY_HIT_RATE_PERCENT', 100);
const missCardinalityPercent = percentageEnv(
    'WALLET_QUERY_MISS_CARDINALITY_PERCENT',
    10,
);
const missPattern = (__ENV.WALLET_QUERY_MISS_PATTERN || MISS_REPEATED).toLowerCase();
const ownerType = (__ENV.WALLET_QUERY_OWNER_TYPE || 'user').toLowerCase();
const systemPercent = percentageEnv('WALLET_QUERY_SYSTEM_PERCENT', 50);

if (!runId || !/^[A-Za-z0-9-]{1,12}$/.test(runId)) {
    throw new Error(
        'WALLET_QUERY_RUN_ID is required and must contain 1-12 letters, digits, or hyphens',
    );
}
if (![PROFILE_RPC_DISCOVERY, PROFILE_WORKING_SET_DISCOVERY, PROFILE_SOAK].includes(profile)) {
    throw new Error(
        'WALLET_QUERY_PROFILE must be rpc-discovery, working-set-discovery, or soak',
    );
}
if (![LOOKUP_WALLET_ID, LOOKUP_REF_ID].includes(lookup)) {
    throw new Error('WALLET_QUERY_LOOKUP must be wallet-id or ref-id');
}
if (![MISS_REPEATED, MISS_COLD].includes(missPattern)) {
    throw new Error('WALLET_QUERY_MISS_PATTERN must be repeated or cold');
}
if (!['user', 'system', 'mixed'].includes(ownerType)) {
    throw new Error('WALLET_QUERY_OWNER_TYPE must be user, system, or mixed');
}
if (hitRatePercent < 100 && missCardinalityPercent === 0) {
    throw new Error(
        'WALLET_QUERY_MISS_CARDINALITY_PERCENT must be greater than 0 when misses are enabled',
    );
}
if (maxVUs < preAllocatedVUs) {
    throw new Error('WALLET_QUERY_MAX_VUS must be >= WALLET_QUERY_PREALLOCATED_VUS');
}

const largestWorkingSet = profile === PROFILE_WORKING_SET_DISCOVERY
    ? workingSetStart + workingSetStep * workingSetSteps
    : workingSetSize;
if (largestWorkingSet > fixtureSize) {
    throw new Error('the largest configured working set exceeds WALLET_QUERY_FIXTURE_SIZE');
}

function seconds(value) {
    return `${value}s`;
}

function scenarioOptions() {
    if (profile === PROFILE_WORKING_SET_DISCOVERY) {
        return {
            walletQuery: {
                executor: 'constant-arrival-rate',
                exec: 'walletQuery',
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
        ? [
            { duration: seconds(stageSeconds), target: warmRate },
            ...Array.from({ length: rateSteps }, (_, index) => ({
                duration: seconds(stageSeconds),
                target: warmRate + rateStep * (index + 1),
            })),
            { duration: seconds(stageSeconds), target: 0 },
        ]
        : [
            { duration: seconds(stageSeconds), target: warmRate },
            { duration: seconds(stageSeconds), target: queryRate },
            { duration: seconds(soakSeconds), target: queryRate },
            { duration: seconds(stageSeconds), target: 0 },
        ];
    return {
        walletQuery: {
            executor: 'ramping-arrival-rate',
            exec: 'walletQuery',
            startRate: warmRate,
            timeUnit: '1s',
            stages,
            preAllocatedVUs,
            maxVUs,
            gracefulStop: seconds(deadlineSeconds * 2 + 5),
        },
    };
}

const operation = lookup === LOOKUP_WALLET_ID
    ? 'getSnapshotByWalletId'
    : 'getSnapshotByRefId';
const grpcMethod = `wallet.WalletService/${operation}`;
const metricTags = { operation, lookup, owner_type: ownerType };
const thresholds = {
    wallet_query_offered: ['count>0'],
    wallet_query_completed: ['count>0'],
};
const maxUnexpectedErrorRate = optionalRateEnv('WALLET_QUERY_MAX_UNEXPECTED_ERROR_RATE');
if (maxUnexpectedErrorRate !== null) {
    thresholds.wallet_query_unexpected_error_rate = [
        `rate<${maxUnexpectedErrorRate}`,
    ];
}
const maxP95Ms = __ENV.WALLET_QUERY_MAX_P95_MS
    ? integerEnv('WALLET_QUERY_MAX_P95_MS', 0)
    : null;
if (maxP95Ms !== null) {
    thresholds[`wallet_query_request_duration{operation:${operation}}`] = [
        `p(95)<${maxP95Ms}`,
    ];
}
if (hitRatePercent < 100 && missPattern === MISS_COLD) {
    thresholds.wallet_query_cold_miss_reuse = ['count==0'];
}

export const options = {
    scenarios: scenarioOptions(),
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    tags: {
        testid: runId,
        test_type: 'wallet_query',
        test_profile: profile,
        test_lookup: lookup,
        owner_type: ownerType,
    },
};

function paddedIndex(index) {
    return String(index).padStart(7, '0');
}

function currentWorkingSetSize() {
    if (profile !== PROFILE_WORKING_SET_DISCOVERY) {
        return workingSetSize;
    }
    // Only the working-set dimension changes during this profile; offered RPC
    // remains fixed so cache-locality effects are not mixed with rate effects.
    const elapsedSeconds = Math.max(
        0,
        (Date.now() - Number(exec.scenario.startTime)) / 1000,
    );
    const step = Math.min(Math.floor(elapsedSeconds / stageSeconds), workingSetSteps);
    return workingSetStart + workingSetStep * step;
}

function walletId(index) {
    return `qw:${runId}:${paddedIndex(index)}`;
}

function walletRef(index) {
    return `qr:${runId}:${paddedIndex(index)}`;
}

function expectedOwner(index) {
    if (ownerType === 'system') return 'OwnerTypePb_System';
    if (ownerType === 'user') return 'OwnerTypePb_User';
    return index % 100 < systemPercent
        ? 'OwnerTypePb_System'
        : 'OwnerTypePb_User';
}

function missOrdinalFor(sequence) {
    const missRatePercent = 100 - hitRatePercent;
    return Math.floor(sequence / 100) * missRatePercent
        + Math.max(0, (sequence % 100) - hitRatePercent);
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
    const missIndex = missOrdinal % missSetSize;
    const prefix = lookup === LOOKUP_WALLET_ID ? 'mqw' : 'mqr';
    return `${prefix}:${runId}:${paddedIndex(missIndex)}`;
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

function validHit(response, expectedIndex, expectedLookup) {
    if (!response || response.status !== grpc.StatusOK || !isBusinessSuccess(response)) {
        return false;
    }
    const snapshot = response.message && response.message.transaction;
    if (!snapshot) return false;
    const lookupMatches = lookup === LOOKUP_WALLET_ID
        ? snapshot.walletId === expectedLookup
        : snapshot.walletReferenceId === expectedLookup;
    return lookupMatches
        && snapshot.walletId === walletId(expectedIndex)
        && snapshot.walletReferenceId === walletRef(expectedIndex)
        && snapshot.ownerType === expectedOwner(expectedIndex)
        && String(snapshot.available) === String(1000000 + expectedIndex)
        && String(snapshot.reserved) === '0';
}

function logFailureSample(classification, responseOrError, expectedLookup) {
    if (loggedFailureCount >= 3) return;
    loggedFailureCount += 1;
    console.error(JSON.stringify({ classification, expectedLookup, responseOrError }));
}

export async function walletQuery() {
    if (!connected) {
        try {
            client.connect(`${walletHost}:${walletPort}`, {
                plaintext: true,
                timeout: `${deadlineSeconds}s`,
            });
            connected = true;
        } catch (error) {
            connectionFailures.add(1, metricTags);
            unexpectedFailures.add(1, { ...metricTags, outcome: 'connection_failure' });
            unexpectedErrorRate.add(true, { ...metricTags, outcome: 'connection_failure' });
            logFailureSample('connection_failure', String(error), null);
            check(null, { 'wallet gRPC connection established': () => false }, metricTags);
            return;
        }
    }

    const sequence = exec.scenario.iterationInTest;
    const activeWorkingSet = currentWorkingSetSize();
    // Hits occupy the first configured percentage of every 100 requests. This
    // makes the ratio deterministic across VUs and independent of randomness.
    const expectedHit = sequence % 100 < hitRatePercent;
    const hitIndex = sequence % activeWorkingSet;
    const expectedLookup = expectedHit
        ? (lookup === LOOKUP_WALLET_ID ? walletId(hitIndex) : walletRef(hitIndex))
        : missLookupValue(sequence, activeWorkingSet);
    const request = lookup === LOOKUP_WALLET_ID
        ? { walletId: expectedLookup }
        : { referenceId: expectedLookup, serviceId: 'ServiceIdPb_User' };

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
        if (!expectedHit && response && response.status === grpc.StatusOK && isNotFound(response)) {
            classification = 'expected_not_found';
            unexpected = false;
            expectedMisses.add(1, metricTags);
            missDuration.add(elapsedMs, metricTags);
        } else if (expectedHit && validHit(response, hitIndex, expectedLookup)) {
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
            logFailureSample(classification, response, expectedLookup);
        }

        const resultTags = { ...metricTags, outcome: classification };
        unexpectedErrorRate.add(unexpected, resultTags);
        check(response, {
            'wallet query returned the expected outcome': () => !unexpected,
        }, resultTags);
    } catch (error) {
        const elapsedMs = Date.now() - startedAt;
        const resultTags = { ...metricTags, outcome: 'transport_exception' };
        requestDuration.add(elapsedMs, metricTags);
        unexpectedFailures.add(1, resultTags);
        unexpectedErrorRate.add(true, resultTags);
        logFailureSample('transport_exception', String(error), expectedLookup);
        check(null, {
            'wallet query returned the expected outcome': () => false,
        }, resultTags);
    }
}
