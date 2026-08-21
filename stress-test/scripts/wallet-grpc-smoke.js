import grpc from 'k6/net/grpc';
import { check, fail, sleep } from 'k6';

// Parse protobuf definitions before measured requests begin. The wallet file
// imports its request messages and the shared error contract from /proto.
const client = new grpc.Client();
client.load(['/proto'], 'wallet/wallet_service.proto');

const walletHost = __ENV.WALLET_GRPC_HOST || '172.31.23.255';
const walletPort = __ENV.WALLET_GRPC_PORT || '9192';
const deadlineSeconds = Number(__ENV.WALLET_GRPC_DEADLINE_SECONDS || '5');
const settlementTimeoutSeconds = Number(
    __ENV.WALLET_SMOKE_SETTLEMENT_TIMEOUT_SECONDS || '20',
);
const pollIntervalSeconds = Number(
    __ENV.WALLET_SMOKE_POLL_INTERVAL_SECONDS || '0.25',
);
const initialBalance = parseInteger(
    'WALLET_SMOKE_INITIAL_BALANCE',
    __ENV.WALLET_SMOKE_INITIAL_BALANCE || '1000',
    true,
);
const reserveAmount = parseInteger(
    'WALLET_SMOKE_RESERVE_AMOUNT',
    __ENV.WALLET_SMOKE_RESERVE_AMOUNT || '100',
    false,
);
const atomicAmount = parseInteger(
    'WALLET_SMOKE_ATOMIC_AMOUNT',
    __ENV.WALLET_SMOKE_ATOMIC_AMOUNT || '25',
    false,
);
const runId = __ENV.WALLET_SMOKE_RUN_ID;

if (!runId) {
    throw new Error('WALLET_SMOKE_RUN_ID must be provided by run-wallet-smoke.sh');
}
if (!Number.isFinite(deadlineSeconds) || deadlineSeconds <= 0) {
    throw new Error('WALLET_GRPC_DEADLINE_SECONDS must be positive');
}
if (!Number.isFinite(settlementTimeoutSeconds) || settlementTimeoutSeconds <= 0) {
    throw new Error('WALLET_SMOKE_SETTLEMENT_TIMEOUT_SECONDS must be positive');
}
if (!Number.isFinite(pollIntervalSeconds) || pollIntervalSeconds <= 0) {
    throw new Error('WALLET_SMOKE_POLL_INTERVAL_SECONDS must be positive');
}
if (reserveAmount > initialBalance || atomicAmount > initialBalance) {
    throw new Error('reserve and atomic amounts must not exceed the initial balance');
}

const assetId = 'k6-wallet-smoke-asset';
const outWalletId = `k6-ws-out-wallet-${runId}`;
const inWalletId = `k6-ws-in-wallet-${runId}`;
const outWalletRef = `k6-ws-out-ref-${runId}`;
const inWalletRef = `k6-ws-in-ref-${runId}`;

// One iteration verifies API correctness and the asynchronous wallet-ledger
// completion path. Failed checks or RPC deadlines make k6 exit nonzero.
export const options = {
    scenarios: {
        walletGrpcSmoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: `${settlementTimeoutSeconds + deadlineSeconds * 8 + 5}s`,
        },
    },
    thresholds: {
        checks: ['rate==1'],
        grpc_req_duration: [`max<${deadlineSeconds * 1000}`],
    },
};

function parseInteger(name, value, allowZero) {
    const pattern = allowZero ? /^\d+$/ : /^[1-9]\d*$/;
    if (!pattern.test(value)) {
        const qualifier = allowZero ? 'a non-negative' : 'a positive';
        throw new Error(`${name} must be ${qualifier} integer`);
    }
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed)) {
        throw new Error(`${name} must fit in JavaScript's safe integer range`);
    }
    return parsed;
}

// ProtoJSON may omit the default ERROR_OK enum. No error object, no code,
// numeric zero, and the enum name all represent a successful business result.
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

function requestParams(operation) {
    return {
        timeout: `${deadlineSeconds}s`,
        tags: { operation },
    };
}

function invoke(operation, request) {
    return client.invoke(
        `wallet.WalletService/${operation}`,
        request,
        requestParams(operation),
    );
}

function logFailure(operation, response) {
    console.error(`${operation} failed: ${JSON.stringify(response)}`);
}

function isGrpcAndBusinessSuccess(response) {
    return response
        && response.status === grpc.StatusOK
        && hasSuccessfulBusinessResult(response);
}

function snapshotOf(response) {
    return response && response.message && response.message.transaction;
}

function amountEquals(value, expected) {
    // k6 exposes protobuf int64 values as strings to preserve precision.
    return String(value) === String(expected);
}

function queryByWalletId(walletId) {
    return invoke('getSnapshotByWalletId', { walletId });
}

export default function () {
    // Reuse one HTTP/2 connection so reconnect time is not charged to each RPC.
    client.connect(`${walletHost}:${walletPort}`, {
        plaintext: true,
        timeout: `${deadlineSeconds}s`,
    });

    const byId = queryByWalletId(outWalletId);
    const byIdPassed = check(byId, {
        'getSnapshotByWalletId gRPC and business result are OK':
            isGrpcAndBusinessSuccess,
        'getSnapshotByWalletId returns the fixture wallet':
            (response) => snapshotOf(response)
                && snapshotOf(response).walletId === outWalletId,
        'outgoing wallet starts with the fixture balance':
            (response) => snapshotOf(response)
                && amountEquals(snapshotOf(response).available, initialBalance)
                && amountEquals(snapshotOf(response).reserved, 0),
    });
    if (!byIdPassed) {
        logFailure('getSnapshotByWalletId', byId);
        client.close();
        fail('initial wallet-id snapshot checks failed');
    }

    const byRef = invoke('getSnapshotByRefId', {
        referenceId: outWalletRef,
        serviceId: 'ServiceIdPb_User',
    });
    const byRefPassed = check(byRef, {
        'getSnapshotByRefId gRPC and business result are OK':
            isGrpcAndBusinessSuccess,
        'getSnapshotByRefId returns the fixture reference':
            (response) => snapshotOf(response)
                && snapshotOf(response).walletReferenceId === outWalletRef,
        'snapshot lookup APIs return the same wallet':
            (response) => snapshotOf(response)
                && snapshotOf(response).walletId === outWalletId,
    });
    if (!byRefPassed) {
        logFailure('getSnapshotByRefId', byRef);
        client.close();
        fail('initial reference snapshot checks failed');
    }

    // Keep request references short because reservation_ref is derived as
    // "request reference:wallet ID" and the database column is varchar(64).
    const reserveRef = `r-${runId}`;
    const reserve = invoke('reserveTransaction', {
        referenceId: reserveRef,
        initiator: 'ServiceIdPb_User',
        idempotencyKey: reserveRef,
        businessType: 'BusinessTypePb_Transfer',
        lines: [{
            walletRef: outWalletRef,
            assetCode: assetId,
            operationType: 'OperationTypePb_Reserve',
            amount: String(reserveAmount),
        }],
    });
    const reservePassed = check(reserve, {
        'reserveTransaction gRPC and business result are OK':
            isGrpcAndBusinessSuccess,
        'reserveTransaction is completed synchronously':
            (response) => response && response.message
                && response.message.status === 'TransactionStatusPb_Completed',
        'reserveTransaction returns one reservation':
            (response) => response && response.message
                && response.message.infos
                && response.message.infos.length === 1
                && amountEquals(response.message.infos[0].amount, reserveAmount),
    });
    if (!reservePassed) {
        logFailure('reserveTransaction', reserve);
        client.close();
        fail('reserveTransaction smoke checks failed');
    }

    const reservationRef = reserve.message.infos[0].reservationRef;
    const reservedSnapshot = queryByWalletId(outWalletId);
    const reservedSnapshotPassed = check(reservedSnapshot, {
        'reserveTransaction moves available funds to reserved':
            (response) => isGrpcAndBusinessSuccess(response)
                && amountEquals(
                    snapshotOf(response).available,
                    initialBalance - reserveAmount,
                )
                && amountEquals(snapshotOf(response).reserved, reserveAmount),
    });
    if (!reservedSnapshotPassed) {
        logFailure('getSnapshotByWalletId after reserve', reservedSnapshot);
        client.close();
        fail('reserved snapshot checks failed');
    }

    // A pure release does not post to ledger. It verifies that the reservation
    // can be applied and that wallet balance invariants are restored.
    const releaseRef = `l-${runId}`;
    const release = invoke('applyReserveTransaction', {
        referenceId: releaseRef,
        initiator: 'ServiceIdPb_User',
        idempotencyKey: releaseRef,
        businessType: 'BusinessTypePb_Transfer',
        lines: [{
            walletRef: outWalletRef,
            assetCode: assetId,
            operationType: 'OperationTypePb_Release',
            amount: String(reserveAmount),
            reservationRef,
        }],
    });
    const releasePassed = check(release, {
        'applyReserveTransaction gRPC and business result are OK':
            isGrpcAndBusinessSuccess,
        'pure release is completed synchronously':
            (response) => response && response.message
                && response.message.status === 'TransactionStatusPb_Completed',
        'applyReserveTransaction returns a transaction ID':
            (response) => Boolean(response
                && response.message
                && response.message.transactionId),
    });
    if (!releasePassed) {
        logFailure('applyReserveTransaction', release);
        client.close();
        fail('applyReserveTransaction smoke checks failed');
    }

    const releasedSnapshot = queryByWalletId(outWalletId);
    const releasedSnapshotPassed = check(releasedSnapshot, {
        'pure release restores the original snapshot':
            (response) => isGrpcAndBusinessSuccess(response)
                && amountEquals(snapshotOf(response).available, initialBalance)
                && amountEquals(snapshotOf(response).reserved, 0),
    });
    if (!releasedSnapshotPassed) {
        logFailure('getSnapshotByWalletId after release', releasedSnapshot);
        client.close();
        fail('released snapshot checks failed');
    }

    const atomicRef = `a-${runId}`;
    const atomic = invoke('atomicTransaction', {
        referenceId: atomicRef,
        initiator: 'ServiceIdPb_User',
        idempotencyKey: atomicRef,
        businessType: 'BusinessTypePb_Transfer',
        lines: [
            {
                walletRef: outWalletRef,
                assetCode: assetId,
                operationType: 'OperationTypePb_Debit',
                amount: String(atomicAmount),
            },
            {
                walletRef: inWalletRef,
                assetCode: assetId,
                operationType: 'OperationTypePb_Credit',
                amount: String(atomicAmount),
            },
        ],
    });
    const atomicPassed = check(atomic, {
        'atomicTransaction gRPC and business result are OK':
            isGrpcAndBusinessSuccess,
        'atomicTransaction is accepted as pending':
            (response) => response && response.message
                && response.message.status === 'TransactionStatusPb_Pending',
        'atomicTransaction returns a transaction ID':
            (response) => Boolean(response
                && response.message
                && response.message.transactionId),
    });
    if (!atomicPassed) {
        logFailure('atomicTransaction', atomic);
        client.close();
        fail('atomicTransaction smoke checks failed');
    }

    // Do not record failed checks while Kafka and ledger are still processing.
    // Only the final state is asserted after the settlement timeout.
    const settlementDeadline = Date.now() + settlementTimeoutSeconds * 1000;
    let settledOut;
    let settledIn;
    while (Date.now() < settlementDeadline) {
        settledOut = queryByWalletId(outWalletId);
        settledIn = queryByWalletId(inWalletId);
        const outSnapshot = snapshotOf(settledOut);
        const inSnapshot = snapshotOf(settledIn);
        if (isGrpcAndBusinessSuccess(settledOut)
            && isGrpcAndBusinessSuccess(settledIn)
            && outSnapshot
            && inSnapshot
            && amountEquals(outSnapshot.available, initialBalance - atomicAmount)
            && amountEquals(outSnapshot.reserved, 0)
            && amountEquals(inSnapshot.available, atomicAmount)
            && amountEquals(inSnapshot.reserved, 0)) {
            break;
        }
        sleep(pollIntervalSeconds);
    }

    const outgoingSettled = check(settledOut, {
        'atomic debit is settled in the outgoing snapshot':
            (response) => isGrpcAndBusinessSuccess(response)
                && snapshotOf(response)
                && amountEquals(
                    snapshotOf(response).available,
                    initialBalance - atomicAmount,
                )
                && amountEquals(snapshotOf(response).reserved, 0),
    });
    const incomingSettled = check(settledIn, {
        'atomic credit is settled in the incoming snapshot':
            (response) => isGrpcAndBusinessSuccess(response)
                && snapshotOf(response)
                && amountEquals(snapshotOf(response).available, atomicAmount)
                && amountEquals(snapshotOf(response).reserved, 0),
    });
    if (!outgoingSettled || !incomingSettled) {
        logFailure('outgoing snapshot after atomic settlement', settledOut);
        logFailure('incoming snapshot after atomic settlement', settledIn);
    }

    client.close();
}
