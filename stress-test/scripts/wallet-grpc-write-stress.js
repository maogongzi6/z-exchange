import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const client = new grpc.Client();
client.load(['/proto'], 'wallet/wallet_service.proto');

const PROFILE_DISCOVERY = 'discovery';
const PROFILE_SOAK = 'soak';
const OPERATION_ATOMIC = 'atomic';
const OPERATION_RESERVE = 'reserve';
const OPERATION_APPLY_RELEASE = 'apply-release';
const OPERATION_APPLY_EARMARK = 'apply-earmark';
const REQUEST_IN_PROCESSING_MESSAGE = 'request_in_processing';

let connected = false;
let loggedFailureCount = 0;

// These counters distinguish load offered to the service from durable-looking
// acceptances and expected contention. Arrival-rate configuration alone cannot
// prove that the generator completed the requested traffic.
const offeredAttempts = new Counter('wallet_write_offered');
const completedAttempts = new Counter('wallet_write_completed');
const successfulAttempts = new Counter('wallet_write_successful');
const processingAttempts = new Counter('wallet_write_request_in_processing');
const unexpectedFailures = new Counter('wallet_write_unexpected_failures');
const connectionFailures = new Counter('wallet_write_connection_failures');
const consistencyFailures = new Counter('wallet_write_consistency_failures');
const unexpectedErrorRate = new Rate('wallet_write_unexpected_error_rate');
const requestDuration = new Trend('wallet_write_request_duration', true);
const successfulDuration = new Trend('wallet_write_success_duration', true);

function integerEnv(name, fallback, minimum = 1) {
    const raw = __ENV[name];
    const value = raw === undefined || raw === '' ? fallback : Number(raw);
    if (!Number.isSafeInteger(value) || value < minimum) {
        throw new Error(`${name} must be an integer >= ${minimum}`);
    }
    return value;
}

function percentageEnv(name, fallback, maximum = 100) {
    const value = integerEnv(name, fallback, 0);
    if (value > maximum) {
        throw new Error(`${name} must be an integer between 0 and ${maximum}`);
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

const walletHost = __ENV.WALLET_GRPC_HOST || '172.31.23.255';
const walletPort = __ENV.WALLET_GRPC_PORT || '9192';
const deadlineSeconds = integerEnv('WALLET_GRPC_DEADLINE_SECONDS', 5);
const duplicateDelaySeconds = integerEnv(
    'WALLET_STRESS_DUPLICATE_DELAY_SECONDS',
    deadlineSeconds,
    0,
);
const profile = (__ENV.WALLET_STRESS_PROFILE || PROFILE_DISCOVERY).toLowerCase();
const operation = (__ENV.WALLET_WRITE_OPERATION || OPERATION_ATOMIC).toLowerCase();
const runId = __ENV.WALLET_STRESS_RUN_ID;
const walletCount = integerEnv('WALLET_STRESS_WALLET_COUNT', 100);
const fixtureRequests = integerEnv('WALLET_STRESS_FIXTURE_REQUESTS', 1);
const baseAmount = integerEnv('WALLET_STRESS_BASE_AMOUNT', 100);
const amountSpan = integerEnv('WALLET_STRESS_AMOUNT_SPAN', 900);
const duplicatePercent = percentageEnv('WALLET_STRESS_DUPLICATE_PERCENT', 5, 50);

if (!runId || !/^[A-Za-z0-9-]{1,12}$/.test(runId)) {
    throw new Error(
        'WALLET_STRESS_RUN_ID is required and must contain 1-12 letters, digits, or hyphens',
    );
}
if (![PROFILE_DISCOVERY, PROFILE_SOAK].includes(profile)) {
    throw new Error('WALLET_STRESS_PROFILE must be discovery or soak');
}
if (![
    OPERATION_ATOMIC,
    OPERATION_RESERVE,
    OPERATION_APPLY_RELEASE,
    OPERATION_APPLY_EARMARK,
].includes(operation)) {
    throw new Error(
        'WALLET_WRITE_OPERATION must be atomic, reserve, apply-release, or apply-earmark',
    );
}
if ((operation === OPERATION_ATOMIC || operation === OPERATION_APPLY_EARMARK)
    && walletCount < 2) {
    throw new Error(`${operation} requires WALLET_STRESS_WALLET_COUNT >= 2`);
}

const stageSeconds = integerEnv('WALLET_STRESS_STAGE_SECONDS', 30);
const warmRate = integerEnv('WALLET_STRESS_WARM_RATE', 5);
const discoveryRateStep = integerEnv('WALLET_STRESS_DISCOVERY_RATE_STEP', 10);
const discoverySteps = integerEnv('WALLET_STRESS_DISCOVERY_STEPS', 8);
const sustainableRate = integerEnv('WALLET_STRESS_SUSTAINABLE_RATE', 50);
const soakSeconds = integerEnv('WALLET_STRESS_SOAK_SECONDS', 1800);
const preAllocatedVUs = integerEnv('WALLET_STRESS_PREALLOCATED_VUS', 100);
const maxVUs = integerEnv('WALLET_STRESS_MAX_VUS', 500);

if (maxVUs < preAllocatedVUs) {
    throw new Error('WALLET_STRESS_MAX_VUS must be >= WALLET_STRESS_PREALLOCATED_VUS');
}

function seconds(value) {
    return `${value}s`;
}

function profileStages() {
    if (profile === PROFILE_DISCOVERY) {
        const stages = [{ duration: seconds(stageSeconds), target: warmRate }];
        for (let step = 1; step <= discoverySteps; step += 1) {
            stages.push({
                duration: seconds(stageSeconds),
                target: warmRate + discoveryRateStep * step,
            });
        }
        stages.push({ duration: seconds(stageSeconds), target: 0 });
        return stages;
    }

    const soakRate = Math.max(1, Math.round(sustainableRate * 0.8));
    return [
        { duration: seconds(stageSeconds), target: warmRate },
        { duration: seconds(stageSeconds), target: soakRate },
        { duration: seconds(soakSeconds), target: soakRate },
        { duration: seconds(stageSeconds), target: 0 },
    ];
}

function grpcOperation() {
    if (operation === OPERATION_ATOMIC) {
        return 'atomicTransaction';
    }
    if (operation === OPERATION_RESERVE) {
        return 'reserveTransaction';
    }
    return 'applyReserveTransaction';
}

const apiOperation = grpcOperation();
const metricTags = { operation: apiOperation, workload: operation };
const thresholds = {
    // A disconnected or undersized generator must not produce a green run
    // merely because optional latency/error SLOs were left unset.
    wallet_write_offered: ['count>0'],
    wallet_write_completed: ['count>0'],
};
const maxUnexpectedErrorRate = optionalRateEnv('WALLET_STRESS_MAX_UNEXPECTED_ERROR_RATE');
if (maxUnexpectedErrorRate !== null) {
    thresholds.wallet_write_unexpected_error_rate = [
        `rate<${maxUnexpectedErrorRate}`,
    ];
}
const maxP95Ms = __ENV.WALLET_STRESS_MAX_P95_MS
    ? integerEnv('WALLET_STRESS_MAX_P95_MS', 0)
    : null;
if (maxP95Ms !== null) {
    thresholds[`wallet_write_request_duration{operation:${apiOperation}}`] = [
        `p(95)<${maxP95Ms}`,
    ];
}

export const options = {
    scenarios: {
        walletWrite: {
            executor: 'ramping-arrival-rate',
            exec: 'walletWrite',
            startRate: warmRate,
            timeUnit: '1s',
            preAllocatedVUs,
            maxVUs,
            stages: profileStages(),
            gracefulStop: seconds(deadlineSeconds * 2 + duplicateDelaySeconds + 5),
        },
    },
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    tags: {
        testid: runId,
        test_type: 'wallet_write',
        test_profile: profile,
        test_operation: operation,
        wallet_count: String(walletCount),
    },
};

function paddedIndex(index) {
    return String(index).padStart(7, '0');
}

function walletRef(index) {
    return `wr:${runId}:${paddedIndex(index)}`;
}

function reservationRef(sequence) {
    return `rv:${runId}:${paddedIndex(sequence)}`;
}

function operationCode() {
    if (operation === OPERATION_ATOMIC) return 'a';
    if (operation === OPERATION_RESERVE) return 'r';
    if (operation === OPERATION_APPLY_RELEASE) return 'l';
    return 'e';
}

function amountFor(sequence) {
    return String(baseAmount + (sequence % amountSpan));
}

// Request data is reconstructed from the logical sequence by both k6 and SQL.
// Short identifiers leave room for wallet-service to derive reservation refs
// inside its varchar(64) business-key columns.
function buildRequest(sequence) {
    const source = sequence % walletCount;
    const destination = (source + 1) % walletCount;
    const referenceId = `wt:${runId}:${operationCode()}:${paddedIndex(sequence)}`;
    const amount = amountFor(sequence);
    const common = {
        referenceId,
        initiator: 'ServiceIdPb_User',
        idempotencyKey: referenceId,
        businessType: 'BusinessTypePb_Transfer',
    };

    if (operation === OPERATION_ATOMIC) {
        return {
            ...common,
            lines: [{
                walletRef: walletRef(source),
                assetCode: `wa:${runId}`,
                operationType: 'OperationTypePb_Debit',
                amount,
            }, {
                walletRef: walletRef(destination),
                assetCode: `wa:${runId}`,
                operationType: 'OperationTypePb_Credit',
                amount,
            }],
        };
    }
    if (operation === OPERATION_RESERVE) {
        return {
            ...common,
            lines: [{
                walletRef: walletRef(source),
                assetCode: `wa:${runId}`,
                operationType: 'OperationTypePb_Reserve',
                amount,
            }],
        };
    }

    const applyLine = {
        walletRef: walletRef(source),
        assetCode: `wa:${runId}`,
        operationType: operation === OPERATION_APPLY_RELEASE
            ? 'OperationTypePb_Release'
            : 'OperationTypePb_Earmark',
        amount,
        reservationRef: reservationRef(sequence),
    };
    return {
        ...common,
        lines: operation === OPERATION_APPLY_RELEASE
            ? [applyLine]
            : [applyLine, {
                walletRef: walletRef(destination),
                assetCode: `wa:${runId}`,
                operationType: 'OperationTypePb_Credit',
                amount,
            }],
    };
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

function isRequestInProcessing(response) {
    const error = businessError(response);
    return Boolean(error)
        && (error.code === 1002 || error.code === 'ERROR_PROCESSING')
        && error.message === REQUEST_IN_PROCESSING_MESSAGE;
}

function validSuccess(response) {
    if (!response || response.status !== grpc.StatusOK || !isBusinessSuccess(response)) {
        return false;
    }
    const message = response.message || {};
    if (!message.transactionId) {
        return false;
    }
    const expectedStatus = operation === OPERATION_RESERVE
        || operation === OPERATION_APPLY_RELEASE
        ? 'TransactionStatusPb_Completed'
        : 'TransactionStatusPb_Pending';
    // Idempotent replay replies contain the original transaction ID but may
    // omit status, which ProtoJSON exposes as unknown or absent.
    return !message.status
        || message.status === 'TransactionStatusPb_Unknown'
        || message.status === expectedStatus;
}

function logFailureSample(classification, responseOrError) {
    if (loggedFailureCount >= 3) {
        return;
    }
    loggedFailureCount += 1;
    console.error(JSON.stringify({ classification, operation, responseOrError }));
}

async function invokeAttempt(request, processingExpected) {
    offeredAttempts.add(1, metricTags);
    const startedAt = Date.now();
    try {
        const response = await client.asyncInvoke(
            `wallet.WalletService/${apiOperation}`,
            request,
            { timeout: `${deadlineSeconds}s`, tags: metricTags },
        );
        const elapsedMs = Date.now() - startedAt;
        completedAttempts.add(1, metricTags);
        requestDuration.add(elapsedMs, metricTags);

        let classification;
        let unexpected;
        let transactionId = null;
        if (validSuccess(response)) {
            classification = 'success';
            unexpected = false;
            transactionId = response.message.transactionId;
            successfulAttempts.add(1, metricTags);
            successfulDuration.add(elapsedMs, metricTags);
        } else if (processingExpected && isRequestInProcessing(response)) {
            classification = 'expected_processing';
            unexpected = false;
            processingAttempts.add(1, metricTags);
        } else {
            classification = !response || response.status !== grpc.StatusOK
                ? 'transport_error'
                : 'unexpected_business_result';
            unexpected = true;
            unexpectedFailures.add(1, { ...metricTags, outcome: classification });
            logFailureSample(classification, response);
        }

        const resultTags = { ...metricTags, outcome: classification };
        unexpectedErrorRate.add(unexpected, resultTags);
        check(response, {
            'wallet write returned the expected outcome': () => !unexpected,
        }, resultTags);
        return { classification, transactionId };
    } catch (error) {
        const elapsedMs = Date.now() - startedAt;
        const resultTags = { ...metricTags, outcome: 'transport_exception' };
        requestDuration.add(elapsedMs, metricTags);
        unexpectedFailures.add(1, resultTags);
        unexpectedErrorRate.add(true, resultTags);
        logFailureSample('transport_exception', String(error));
        check(null, {
            'wallet write returned the expected outcome': () => false,
        }, resultTags);
        return { classification: 'transport_exception', transactionId: null };
    }
}

// In each full group, U=100-P logical requests create U originals and the
// first P create one extra duplicate. The resulting attempt population is
// exactly P percent duplicates. Even groups race concurrently; odd groups
// exercise replay after a configurable delay.
function duplicateModeFor(sequence) {
    if (duplicatePercent === 0) {
        return 'none';
    }
    const uniquePerCycle = 100 - duplicatePercent;
    const position = sequence % uniquePerCycle;
    if (position >= duplicatePercent) {
        return 'none';
    }
    return position % 2 === 0 ? 'concurrent' : 'delayed';
}

function verifyDuplicateConsistency(results) {
    const successfulIds = results
        .map((result) => result.transactionId)
        .filter((value) => value !== null);
    if (successfulIds.length < 2) {
        return;
    }
    const consistent = successfulIds.every((value) => value === successfulIds[0]);
    if (!consistent) {
        consistencyFailures.add(1, metricTags);
        unexpectedFailures.add(1, { ...metricTags, outcome: 'duplicate_txn_conflict' });
        unexpectedErrorRate.add(true, { ...metricTags, outcome: 'duplicate_txn_conflict' });
        logFailureSample('duplicate_txn_conflict', successfulIds);
        check(null, {
            'successful duplicates return the same wallet transaction': () => false,
        }, metricTags);
    }
}

export async function walletWrite() {
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
            logFailureSample('connection_failure', String(error));
            check(null, { 'wallet gRPC connection established': () => false }, metricTags);
            return;
        }
    }

    const sequence = exec.scenario.iterationInTest;
    if (sequence >= fixtureRequests) {
        throw new Error(
            `logical sequence ${sequence} exceeds WALLET_STRESS_FIXTURE_REQUESTS=${fixtureRequests}`,
        );
    }
    const request = buildRequest(sequence);
    const duplicateMode = duplicateModeFor(sequence);
    let results;

    if (duplicateMode === 'concurrent') {
        // Start both calls before awaiting either so either request can win the
        // Redis idempotency claim without making the test order-dependent.
        results = await Promise.all([
            invokeAttempt(request, true),
            invokeAttempt(request, true),
        ]);
    } else {
        results = [await invokeAttempt(request, false)];
        if (duplicateMode === 'delayed') {
            sleep(duplicateDelaySeconds);
            results.push(await invokeAttempt(request, false));
        }
    }
    verifyDuplicateConsistency(results);
}
