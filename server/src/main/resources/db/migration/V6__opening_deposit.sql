-- The cash ledger should add up to the balance from its first row. Accounts opened
-- before this migration have no row for their starting $100,000, so their ledger sums
-- to less than their balance. Every such account started with exactly that amount.
-- The row is dated at the account's creation so it sorts before its trades.

INSERT INTO cash_transactions (user_id, order_id, type, amount, balance_after, created_at)
SELECT u.id, NULL, 'DEPOSIT', 100000.0000, 100000.0000, u.created_at
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM cash_transactions c WHERE c.user_id = u.id AND c.type = 'DEPOSIT'
);
