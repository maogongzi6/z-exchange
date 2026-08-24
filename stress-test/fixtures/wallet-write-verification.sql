-- Required session variables:
--   @run_prefix, @run_id, @operation, @base_amount, @amount_span,
--   @wallet_count, @initial_balance

DROP TEMPORARY TABLE IF EXISTS k6_wallet_failures;
CREATE TEMPORARY TABLE k6_wallet_failures (failure_type varchar(64));

INSERT INTO k6_wallet_failures
SELECT 'pending_transaction'
FROM wallet_transactions
WHERE reference_id LIKE CONCAT(@run_prefix, '%') AND txn_status <> 2;

INSERT INTO k6_wallet_failures
SELECT 'duplicate_reference'
FROM wallet_transactions
WHERE reference_id LIKE CONCAT(@run_prefix, '%')
GROUP BY reference_id
HAVING COUNT(*) <> 1;

INSERT INTO k6_wallet_failures
SELECT 'negative_balance'
FROM balance_snapshots
WHERE wallet_id LIKE CONCAT('ww:', @run_id, ':%')
  AND (available < 0 OR reserved < 0);

INSERT INTO k6_wallet_failures
SELECT 'fund_conservation'
FROM balance_snapshots
WHERE wallet_id LIKE CONCAT('ww:', @run_id, ':%')
HAVING SUM(available + reserved) <> @initial_balance * @wallet_count;

INSERT INTO k6_wallet_failures
SELECT 'reservation_invariant'
FROM wallet_reservations reservation
LEFT JOIN wallet_transactions txn ON txn.txn_id = reservation.reserve_txn_id
WHERE (txn.reference_id LIKE CONCAT(@run_prefix, '%')
       OR reservation.reference_id LIKE CONCAT('rv:', @run_id, ':%'))
  AND reservation.total <>
      reservation.remaining + reservation.consumed
      + reservation.pending_settle + reservation.released;

INSERT INTO k6_wallet_failures
SELECT 'created_reservation_final_state'
FROM wallet_transactions txn
JOIN wallet_reservations reservation ON reservation.reserve_txn_id = txn.txn_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
  AND (
      (@operation = 'atomic' AND NOT (
          reservation.remaining = 0
          AND reservation.pending_settle = 0
          AND reservation.consumed = reservation.total
          AND reservation.released = 0
          AND reservation.reservation_status = 2
          AND reservation.reservation_outcome = 2))
      OR
      (@operation = 'reserve' AND NOT (
          reservation.remaining = reservation.total
          AND reservation.pending_settle = 0
          AND reservation.consumed = 0
          AND reservation.released = 0
          AND reservation.reservation_status = 1
          AND reservation.reservation_outcome = 1))
  );

INSERT INTO k6_wallet_failures
SELECT 'applied_reservation_final_state'
FROM wallet_transactions txn
JOIN wallet_actions action
  ON action.txn_id = txn.txn_id AND action.action_type IN (2, 3)
JOIN wallet_reservations reservation ON reservation.reference_id = action.reservation_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
  AND (
      (@operation = 'apply-earmark' AND NOT (
          reservation.remaining = 0
          AND reservation.pending_settle = 0
          AND reservation.consumed = reservation.total
          AND reservation.released = 0
          AND reservation.reservation_status = 2
          AND reservation.reservation_outcome = 2))
      OR
      (@operation = 'apply-release' AND NOT (
          reservation.remaining = 0
          AND reservation.pending_settle = 0
          AND reservation.consumed = 0
          AND reservation.released = reservation.total
          AND reservation.reservation_status = 2
          AND reservation.reservation_outcome = 3))
  );

INSERT INTO k6_wallet_failures
SELECT 'unexpected_action_shape'
FROM wallet_transactions txn
LEFT JOIN wallet_actions action ON action.txn_id = txn.txn_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
GROUP BY txn.txn_id, txn.reference_id
HAVING
    CASE @operation
        WHEN 'atomic' THEN NOT (
            COUNT(action.id) = 4
            AND SUM(action.action_type = 1) = 1
            AND SUM(action.action_type = 2) = 1
            AND SUM(action.action_type = 4) = 1
            AND SUM(action.action_type = 5) = 1)
        WHEN 'reserve' THEN NOT (
            COUNT(action.id) = 1 AND SUM(action.action_type = 1) = 1)
        WHEN 'apply-release' THEN NOT (
            COUNT(action.id) = 1 AND SUM(action.action_type = 3) = 1)
        WHEN 'apply-earmark' THEN NOT (
            COUNT(action.id) = 3
            AND SUM(action.action_type = 2) = 1
            AND SUM(action.action_type = 4) = 1
            AND SUM(action.action_type = 5) = 1)
        ELSE TRUE
    END;

INSERT INTO k6_wallet_failures
SELECT 'unexpected_action_amount'
FROM wallet_transactions txn
JOIN wallet_actions action ON action.txn_id = txn.txn_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
  AND action.amount <>
      @base_amount + MOD(
          CAST(SUBSTRING_INDEX(txn.reference_id, ':', -1) AS UNSIGNED),
          @amount_span);

INSERT INTO k6_wallet_failures
SELECT 'unexpected_created_reservation_count'
FROM wallet_transactions txn
LEFT JOIN wallet_reservations reservation ON reservation.reserve_txn_id = txn.txn_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
GROUP BY txn.txn_id
HAVING COUNT(reservation.id) <>
    CASE WHEN @operation IN ('atomic', 'reserve') THEN 1 ELSE 0 END;

INSERT INTO k6_wallet_failures
SELECT 'unexpected_outbox_shape'
FROM wallet_transactions txn
LEFT JOIN outbox event ON event.command_id = txn.txn_id
WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
GROUP BY txn.txn_id
HAVING
    COUNT(event.id) <>
        CASE WHEN @operation IN ('atomic', 'apply-earmark') THEN 1 ELSE 0 END
    OR SUM(CASE WHEN event.id IS NOT NULL AND event.outbox_status <> 2 THEN 1 ELSE 0 END) > 0;

-- Reconstruct every final balance from the fixture's pre-reserved funds and
-- the committed run actions. This catches equal-and-opposite balance errors
-- that a total-fund conservation check alone would miss.
INSERT INTO k6_wallet_failures
SELECT 'snapshot_payload_mismatch'
FROM balance_snapshots snapshot
LEFT JOIN (
    SELECT wallet_id, SUM(total) AS amount
    FROM wallet_reservations
    WHERE reference_id LIKE CONCAT('rv:', @run_id, ':%')
    GROUP BY wallet_id
) fixture_reserved ON fixture_reserved.wallet_id = snapshot.wallet_id
LEFT JOIN (
    SELECT
        action.wallet_id,
        SUM(CASE WHEN action.action_type = 1 THEN action.amount ELSE 0 END) AS reserved_amount,
        SUM(CASE WHEN action.action_type = 3 THEN action.amount ELSE 0 END) AS released_amount,
        SUM(CASE WHEN action.action_type = 4 THEN action.amount ELSE 0 END) AS transferred_out,
        SUM(CASE WHEN action.action_type = 5 THEN action.amount ELSE 0 END) AS transferred_in
    FROM wallet_actions action
    JOIN wallet_transactions txn ON txn.txn_id = action.txn_id
    WHERE txn.reference_id LIKE CONCAT(@run_prefix, '%')
    GROUP BY action.wallet_id
) run_action ON run_action.wallet_id = snapshot.wallet_id
WHERE snapshot.wallet_id LIKE CONCAT('ww:', @run_id, ':%')
  AND (
      snapshot.available <>
          @initial_balance
          - COALESCE(fixture_reserved.amount, 0)
          - COALESCE(run_action.reserved_amount, 0)
          + COALESCE(run_action.released_amount, 0)
          + COALESCE(run_action.transferred_in, 0)
      OR
      snapshot.reserved <>
          COALESCE(fixture_reserved.amount, 0)
          + COALESCE(run_action.reserved_amount, 0)
          - COALESCE(run_action.released_amount, 0)
          - COALESCE(run_action.transferred_out, 0)
  );

SELECT CONCAT(failure_type, '=', COUNT(*))
FROM k6_wallet_failures
GROUP BY failure_type
ORDER BY failure_type;

SELECT COUNT(*) FROM k6_wallet_failures;
