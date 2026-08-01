import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const client = new grpc.Client();
client.load(['/proto'], 'com/exchange/proto/ledger/post_service.proto');

const POST_TRANSACTION_METHOD = 'ledger.PostService/postTransaction';
const PROFILE_DISCOVERY = 'discovery';
const PROFILE_RECOVERY = 'recovery';
const PROFILE_SOAK = 'soak';
const REQUEST_IN_PROCESSING_MESSAGE = 'request_in_processing';

// Each VU has its own JavaScript runtime and therefore its own connection
// state. Reusing one HTTP/2 connection per VU keeps connection setup outside
// the measured request latency after that VU's first iteration.
let connected = false;
let loggedFailureCount = 0;

// Custom metrics make offered, completed, accepted, and unexpected traffic
// independently visible. A configured arrival rate alone does not prove that
// the load generator actually sent or completed that number of RPCs.
const offeredAttempts = new Counter('ledger_write_offered');
const completedAttempts = new Counter('ledger_write_completed');
const successfulAttempts = new Counter('ledger_write_successful');
const processingAttempts = new Counter('ledger_write_request_in_processing');
const unexpectedFailures = new Counter('ledger_write_unexpected_failures');
const connectionFailures = new Counter('ledger_write_connection_failures');
const consistencyFailures = new Counter('ledger_write_consistency_failures');
const unexpectedErrorRate = new Rate('ledger_write_unexpected_error_rate');
const requestDuration = new Trend('ledger_write_request_duration', true);

function integerEnv(name, fallback, minimum = 1) {
    const raw = __ENV[name];
    const value = raw === undefined || raw === '' ? fallback : Number(raw);
    if (!Number.isSafeInteger(value) || value < minimum) {
        throw new Error(`${name} must be an integer >= ${minimum}`);
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

const ledgerHost = __ENV.LEDGER_GRPC_HOST || '172.31.16.37';
const ledgerPort = __ENV.LEDGER_GRPC_PORT || '9191';
const deadlineSeconds = integerEnv('LEDGER_GRPC_DEADLINE_SECONDS', 5);
const duplicateDelaySeconds = integerEnv(
    'LEDGER_DUPLICATE_DELAY_SECONDS',
    deadlineSeconds,
    0,
);
const baseAmount = integerEnv('LEDGER_STRESS_BASE_AMOUNT', 100);
const amountSpan = integerEnv('LEDGER_STRESS_AMOUNT_SPAN', 900);
const profile = (__ENV.LEDGER_STRESS_PROFILE || PROFILE_DISCOVERY).toLowerCase();
const runId = __ENV.LEDGER_STRESS_RUN_ID;

if (!runId || !/^[A-Za-z0-9-]{1,24}$/.test(runId)) {
    throw new Error(
        'LEDGER_STRESS_RUN_ID is required and must contain 1-24 letters, digits, or hyphens',
    );
}
if (![PROFILE_DISCOVERY, PROFILE_RECOVERY, PROFILE_SOAK].includes(profile)) {
    throw new Error('LEDGER_STRESS_PROFILE must be discovery, recovery, or soak');
}

const stageSeconds = integerEnv('LEDGER_STRESS_STAGE_SECONDS', 30);
const warmRate = integerEnv('LEDGER_STRESS_WARM_RATE', 5);
const discoveryRateStep = integerEnv('LEDGER_STRESS_DISCOVERY_RATE_STEP', 10);
const discoverySteps = integerEnv('LEDGER_STRESS_DISCOVERY_STEPS', 8);
const sustainableRate = integerEnv('LEDGER_STRESS_SUSTAINABLE_RATE', 50);
const recoveryObservationSeconds = integerEnv(
    'LEDGER_STRESS_RECOVERY_OBSERVATION_SECONDS',
    60,
);
const soakSeconds = integerEnv('LEDGER_STRESS_SOAK_SECONDS', 120);
const preAllocatedVUs = integerEnv('LEDGER_STRESS_PREALLOCATED_VUS', 100);
const maxVUs = integerEnv('LEDGER_STRESS_MAX_VUS', 500);

if (maxVUs < preAllocatedVUs) {
    throw new Error('LEDGER_STRESS_MAX_VUS must be >= LEDGER_STRESS_PREALLOCATED_VUS');
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

    if (profile === PROFILE_RECOVERY) {
        const baselineRate = Math.max(1, Math.round(sustainableRate * 0.8));
        const overloadRate = Math.max(1, Math.round(sustainableRate * 1.15));
        const recoveredRate = Math.max(1, Math.round(sustainableRate * 0.6));
        return [
            { duration: seconds(stageSeconds), target: warmRate },
            { duration: seconds(stageSeconds), target: baselineRate },
            { duration: seconds(stageSeconds), target: baselineRate },
            { duration: seconds(stageSeconds), target: overloadRate },
            { duration: seconds(stageSeconds), target: overloadRate },
            { duration: seconds(stageSeconds), target: recoveredRate },
            { duration: seconds(recoveryObservationSeconds), target: recoveredRate },
            { duration: seconds(stageSeconds), target: 0 },
        ];
    }

    const soakRate = Math.max(1, Math.round(sustainableRate * 0.8));
    return [
        { duration: seconds(stageSeconds), target: warmRate },
        { duration: seconds(stageSeconds), target: soakRate },
        { duration: seconds(soakSeconds), target: soakRate },
        { duration: seconds(stageSeconds), target: 0 },
    ];
}

const thresholds = {};
const maxUnexpectedErrorRate = optionalRateEnv('LEDGER_STRESS_MAX_UNEXPECTED_ERROR_RATE');
if (maxUnexpectedErrorRate !== null) {
    thresholds.ledger_write_unexpected_error_rate = [
        `rate<${maxUnexpectedErrorRate}`,
    ];
}
const maxP95Ms = __ENV.LEDGER_STRESS_MAX_P95_MS
    ? integerEnv('LEDGER_STRESS_MAX_P95_MS', 0)
    : null;
if (maxP95Ms !== null) {
    // Only original attempts define the primary write SLO. Fast idempotent
    // duplicate responses must not make the write latency look better.
    thresholds['ledger_write_request_duration{request_kind:original}'] = [
        `p(95)<${maxP95Ms}`,
    ];
}

export const options = {
    scenarios: {
        ledgerWrite: {
            executor: 'ramping-arrival-rate',
            exec: 'ledgerWrite',
            startRate: warmRate,
            timeUnit: '1s',
            preAllocatedVUs,
            maxVUs,
            stages: profileStages(),
            gracefulStop: seconds(deadlineSeconds * 2 + 5),
        },
    },
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    tags: {
        testid: runId,
        test_type: 'ledger_write',
        test_profile: profile,
    },
};

function entryCountFor(sequence) {
    // Twenty consecutive logical transactions produce the exact requested
    // distribution: 9 x 2 entries, 9 x 4 entries, and 2 x 20 entries.
    const bucket = sequence % 20;
    if (bucket < 9) {
        return 2;
    }
    if (bucket < 18) {
        return 4;
    }
    return 20;
}

function fixtureAssetId(assetIndex) {
    return `k6-write-asset-${String(assetIndex).padStart(2, '0')}`;
}

function fixtureAccountRef(accountIndex) {
    return `k6-write-account-${String(accountIndex).padStart(3, '0')}`;
}

function buildRequest(sequence) {
    const entryCount = entryCountFor(sequence);
    const pairCount = entryCount / 2;
    const assetIndex = sequence % 10;
    const assetId = fixtureAssetId(assetIndex);
    const entries = [];

    for (let pair = 0; pair < pairCount; pair += 1) {
        // Each asset owns ten fixture accounts. Rotating the pair's starting
        // offset by sequence spreads requests over those accounts. A 20-entry
        // request deliberately cycles over the complete ten-account set twice.
        const debitOffset = (sequence + pair * 2) % 10;
        const creditOffset = (debitOffset + 1) % 10;
        const amount = String(baseAmount + ((sequence + pair) % amountSpan));

        entries.push({
            accountRef: fixtureAccountRef(assetIndex * 10 + debitOffset),
            direction: 'LedgerDirection_Debit',
            amount,
            assetId,
        });
        entries.push({
            accountRef: fixtureAccountRef(assetIndex * 10 + creditOffset),
            direction: 'LedgerDirection_Credit',
            amount,
            assetId,
        });
    }

    const referenceId = `k6-write:${runId}:${sequence}`;
    if (referenceId.length > 64) {
        throw new Error(`generated reference ID exceeds 64 characters: ${referenceId}`);
    }
    return {
        referenceId,
        description: `k6 ledger write ${runId}`,
        entries,
    };
}

function isBusinessSuccess(response) {
    const error = response && response.message && response.message.error;
    return !error
        || error.code === undefined
        || error.code === 0
        || error.code === 'ERROR_OK';
}

function isRequestInProcessing(response) {
    const error = response && response.message && response.message.error;
    if (!error) {
        return false;
    }
    const processingCode = error.code === 1002 || error.code === 'ERROR_PROCESSING';
    return processingCode && error.message === REQUEST_IN_PROCESSING_MESSAGE;
}

function metricTags(request, requestKind, duplicateTiming) {
    return {
        operation: 'postTransaction',
        request_kind: requestKind,
        duplicate_timing: duplicateTiming,
        entry_count: String(request.entries.length),
    };
}

function logFailureSample(classification, responseOrError, tags) {
    // Unlimited error logging can become the bottleneck during overload and
    // change the result being measured. Retain only a few samples per VU; use
    // service logs and aggregate metrics for the complete failure picture.
    if (loggedFailureCount >= 3) {
        return;
    }
    loggedFailureCount += 1;
    console.error(JSON.stringify({
        classification,
        tags,
        response: responseOrError,
    }));
}

async function invokeAttempt(request, requestKind, duplicateTiming, processingExpected) {
    const tags = metricTags(request, requestKind, duplicateTiming);
    const startedAt = Date.now();
    offeredAttempts.add(1, tags);

    try {
        const response = await client.asyncInvoke(
            POST_TRANSACTION_METHOD,
            request,
            {
                timeout: `${deadlineSeconds}s`,
                tags,
            },
        );
        const elapsedMs = Date.now() - startedAt;
        completedAttempts.add(1, tags);

        let classification;
        let unexpected = false;
        if (!response || response.status !== grpc.StatusOK) {
            classification = 'transport_error';
            unexpected = true;
        } else if (isBusinessSuccess(response)) {
            const hasRequiredResult = Boolean(
                response.message
                && response.message.referenceId === request.referenceId
                && response.message.ledgerTxnId,
            );
            classification = hasRequiredResult ? 'success' : 'invalid_success';
            unexpected = !hasRequiredResult;
            if (hasRequiredResult) {
                successfulAttempts.add(1, tags);
            }
        } else if (isRequestInProcessing(response)) {
            classification = processingExpected
                ? 'expected_processing'
                : 'unexpected_processing';
            processingAttempts.add(1, {
                ...tags,
                expected: String(processingExpected),
            });
            unexpected = !processingExpected;
        } else {
            classification = 'business_error';
            unexpected = true;
        }

        const resultTags = { ...tags, outcome: classification };
        requestDuration.add(elapsedMs, resultTags);
        unexpectedErrorRate.add(unexpected, resultTags);
        if (unexpected) {
            unexpectedFailures.add(1, resultTags);
            logFailureSample(classification, response, tags);
        }
        check(response, {
            'ledger write returned an accepted outcome': () => !unexpected,
        }, resultTags);

        return {
            classification,
            ledgerTxnId: classification === 'success'
                ? response.message.ledgerTxnId
                : null,
        };
    } catch (error) {
        const resultTags = { ...tags, outcome: 'transport_exception' };
        requestDuration.add(Date.now() - startedAt, resultTags);
        unexpectedErrorRate.add(true, resultTags);
        unexpectedFailures.add(1, resultTags);
        logFailureSample('transport_exception', String(error), tags);
        check(null, {
            'ledger write returned an accepted outcome': () => false,
        }, resultTags);
        return { classification: 'transport_exception', ledgerTxnId: null };
    }
}

function duplicateCountsFor(sequence) {
    // Every 95 logical transactions contain five duplicate attempts:
    //   sequence 31: one concurrent + one delayed duplicate
    //   sequence 73: two concurrent + one delayed duplicate
    // This yields 100 total attempts and an exact 5% duplicate share per full
    // cycle, while ensuring some requests are duplicated more than once.
    const cyclePosition = sequence % 95;
    if (cyclePosition === 31) {
        return { concurrent: 1, delayed: 1 };
    }
    if (cyclePosition === 73) {
        return { concurrent: 2, delayed: 1 };
    }
    return { concurrent: 0, delayed: 0 };
}

function verifyDuplicateTxnIds(results, request) {
    const successfulIds = results
        .filter((result) => result.ledgerTxnId)
        .map((result) => result.ledgerTxnId);
    if (successfulIds.length < 2) {
        return;
    }
    const first = successfulIds[0];
    const consistent = successfulIds.every((txnId) => txnId === first);
    check(results, {
        'successful duplicates return the same ledger transaction': () => consistent,
    }, { operation: 'postTransaction', request_kind: 'duplicate_group' });
    if (!consistent) {
        consistencyFailures.add(1, { reason: 'different_ledger_txn_id' });
        logFailureSample('duplicate_txn_id_conflict', successfulIds, {
            reference_id: request.referenceId,
        });
    }
}

export async function ledgerWrite() {
    if (!connected) {
        try {
            client.connect(`${ledgerHost}:${ledgerPort}`, {
                plaintext: true,
                timeout: `${deadlineSeconds}s`,
            });
            connected = true;
        } catch (error) {
            connectionFailures.add(1);
            unexpectedErrorRate.add(true, { outcome: 'connection_failure' });
            logFailureSample('connection_failure', String(error), {});
            check(null, {
                'ledger gRPC connection established': () => false,
            });
            return;
        }
    }

    // iterationInTest is unique within this single k6 scenario and gives the
    // deterministic sequence used by request generation and SQL reconciliation.
    const sequence = exec.scenario.iterationInTest;
    const request = buildRequest(sequence);
    const duplicateCounts = duplicateCountsFor(sequence);
    const immediateAttempts = [];

    // Starting asyncInvoke calls before awaiting any result makes these RPCs
    // overlap. Server scheduling can let any member win the idempotency claim,
    // so REQUEST_IN_PROCESSING is accepted for every immediate group member.
    immediateAttempts.push(invokeAttempt(
        request,
        'original',
        duplicateCounts.concurrent > 0 ? 'concurrent_group' : 'none',
        duplicateCounts.concurrent > 0,
    ));
    for (let index = 0; index < duplicateCounts.concurrent; index += 1) {
        immediateAttempts.push(invokeAttempt(
            request,
            'duplicate',
            'concurrent',
            true,
        ));
    }

    const results = await Promise.all(immediateAttempts);

    // A delayed duplicate is sent one configured deadline after the immediate
    // group completes. It should replay the committed result; a processing
    // response at this point is classified as unexpected.
    for (let index = 0; index < duplicateCounts.delayed; index += 1) {
        sleep(duplicateDelaySeconds);
        results.push(await invokeAttempt(request, 'duplicate', 'delayed', false));
    }

    verifyDuplicateTxnIds(results, request);
}

export function teardown() {
    // A zero-to-zero arrival-rate stage has no work to schedule, so k6 can
    // render the scenario as interrupted even though the run exits with 0.
    // Keep the idle observation outside the executor: no requests are sent,
    // while service and infrastructure dashboards remain observable.
    sleep(stageSeconds);
}
