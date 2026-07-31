import grpc from 'k6/net/grpc';
import { check, fail } from 'k6';

// k6 evaluates this top-level section once per virtual user before the scenario
// starts. Loading protobuf definitions here keeps schema parsing out of measured
// request time.
const client = new grpc.Client();
client.load(['/proto'], 'com/exchange/proto/ledger/post_service.proto');

// Environment variables let the same image target another ledger deployment.
// JavaScript's || operator applies the value on the right when the value on the
// left is missing or empty.
const ledgerHost = __ENV.LEDGER_GRPC_HOST || '172.31.16.37';
const ledgerPort = __ENV.LEDGER_GRPC_PORT || '9191';
const deadlineSeconds = Number(__ENV.LEDGER_GRPC_DEADLINE_SECONDS || '5');
const amount = __ENV.LEDGER_TEST_AMOUNT || '100';

if (!Number.isFinite(deadlineSeconds) || deadlineSeconds <= 0) {
    throw new Error('LEDGER_GRPC_DEADLINE_SECONDS must be a positive number');
}
if (!/^[1-9]\d*$/.test(amount)) {
    throw new Error('LEDGER_TEST_AMOUNT must be a positive integer');
}

// The shell entrypoint inserts these dedicated fixture rows before k6 starts.
// Keep these values aligned with fixtures/ledger-smoke-fixture.sql.
const assetId = 'k6-smoke-asset';
const accountRef = 'k6-smoke-account';

// A smoke test executes this workflow once. Thresholds make k6 return a nonzero
// exit code when any correctness check fails or a call exceeds the deadline.
export const options = {
    scenarios: {
        ledgerGrpcSmoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            // Allow one connection and three RPC deadlines plus scheduling
            // margin before k6 forcibly stops the smoke iteration.
            maxDuration: `${deadlineSeconds * 4 + 5}s`,
        },
    },
    thresholds: {
        checks: ['rate==1'],
        grpc_req_duration: [`max<${deadlineSeconds * 1000}`],
    },
};

// Protobuf JSON can omit fields containing their default value. ERROR_OK is
// enum value zero, so either an omitted error/code or ERROR_OK means success.
function hasSuccessfulBusinessResult(response) {
    if (!response || !response.message) {
        return false;
    }
    const error = response.message.error;
    return !error
        || error.code === undefined
        || error.code === 0
        || error.code === 'ERROR_OK';
}

// Request-specific tags separate latency and throughput by RPC method without
// creating a high-cardinality tag from transaction or account identifiers.
function requestParams(operation) {
    return {
        timeout: `${deadlineSeconds}s`,
        tags: { operation },
    };
}

// Printing a failed response makes a remote Docker run diagnosable without
// enabling verbose logging for successful requests.
function logFailure(operation, response) {
    console.error(`${operation} failed: ${JSON.stringify(response)}`);
}

export default function () {
    // Each VU owns one long-lived HTTP/2 connection. Reusing it ensures the
    // smoke measures RPC behavior instead of reconnecting for every method.
    client.connect(`${ledgerHost}:${ledgerPort}`, {
        plaintext: true,
        timeout: `${deadlineSeconds}s`,
    });

    // Date.now() plus a random suffix gives every run a new idempotency key.
    // The reference remains below the ledger database's 64-character limit.
    const referenceId =
        `k6-smoke-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;

    // int64 protobuf fields are represented as strings in ProtoJSON to avoid
    // JavaScript number precision loss for values above Number.MAX_SAFE_INTEGER.
    const postResponse = client.invoke(
        'ledger.PostService/postTransaction',
        {
            referenceId,
            description: 'k6 ledger gRPC smoke transaction',
            entries: [
                {
                    accountRef,
                    direction: 'LedgerDirection_Debit',
                    amount,
                    assetId,
                },
                {
                    accountRef,
                    direction: 'LedgerDirection_Credit',
                    amount,
                    assetId,
                },
            ],
        },
        requestParams('postTransaction'),
    );

    const postSucceeded = check(postResponse, {
        'postTransaction gRPC status is OK':
            (response) => response && response.status === grpc.StatusOK,
        'postTransaction business result is OK': hasSuccessfulBusinessResult,
        'postTransaction returns a ledger transaction ID':
            (response) => Boolean(response
                && response.message
                && response.message.ledgerTxnId),
    });

    // The two query calls depend on the transaction created above. Abort this
    // single iteration with a clear error rather than sending meaningless IDs.
    if (!postSucceeded) {
        logFailure('postTransaction', postResponse);
        client.close();
        fail('postTransaction smoke checks failed');
    }

    const ledgerTxnId = postResponse.message.ledgerTxnId;

    const byReferenceResponse = client.invoke(
        'ledger.PostService/getTxnByRefId',
        {
            referenceId,
            includeEntries: true,
        },
        requestParams('getTxnByRefId'),
    );

    const byReferenceSucceeded = check(byReferenceResponse, {
        'getTxnByRefId gRPC status is OK':
            (response) => response && response.status === grpc.StatusOK,
        'getTxnByRefId business result is OK': hasSuccessfulBusinessResult,
        'getTxnByRefId returns the requested reference':
            (response) => response
                && response.message
                && response.message.transaction
                && response.message.transaction.referenceId === referenceId,
        'getTxnByRefId returns both ledger entries':
            (response) => response
                && response.message
                && response.message.entries
                && response.message.entries.length === 2,
    });
    if (!byReferenceSucceeded) {
        logFailure('getTxnByRefId', byReferenceResponse);
    }

    const byIdResponse = client.invoke(
        'ledger.PostService/getTxnById',
        {
            ledgerTxnId,
            includeEntries: true,
        },
        requestParams('getTxnById'),
    );

    const byIdSucceeded = check(byIdResponse, {
        'getTxnById gRPC status is OK':
            (response) => response && response.status === grpc.StatusOK,
        'getTxnById business result is OK': hasSuccessfulBusinessResult,
        'getTxnById returns the requested transaction':
            (response) => response
                && response.message
                && response.message.transaction
                && response.message.transaction.ledgerTxnId === ledgerTxnId,
        'getTxnById returns both ledger entries':
            (response) => response
                && response.message
                && response.message.entries
                && response.message.entries.length === 2,
    });
    if (!byIdSucceeded) {
        logFailure('getTxnById', byIdResponse);
    }

    client.close();
}
