-- Slice 3: market orders. `orders` keeps every request including rejected ones (rule 4),
-- `trades` the executions, `holdings` the current position, `cash_transactions` the
-- ledger. Limit and trigger prices arrive with slice 6, when something can use them.
--
-- `symbol` carries no foreign key to `stocks`: that table only holds symbols a user has
-- searched, while a quote (and so an order) is valid for any symbol Finnhub carries.

CREATE TABLE orders (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    symbol        VARCHAR(20)  NOT NULL,
    side          VARCHAR(4)   NOT NULL,
    type          VARCHAR(10)  NOT NULL,
    quantity      BIGINT       NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    reject_reason VARCHAR(100) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_user_created (user_id, created_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE trades (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    symbol      VARCHAR(20)   NOT NULL,
    side        VARCHAR(4)    NOT NULL,
    quantity    BIGINT        NOT NULL,
    price       DECIMAL(19,4) NOT NULL,
    executed_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_trades_user_executed (user_id, executed_at),
    CONSTRAINT fk_trades_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- A fully sold position stays as a row with quantity 0 so its realized P&L survives.
CREATE TABLE holdings (
    user_id      BIGINT        NOT NULL,
    symbol       VARCHAR(20)   NOT NULL,
    quantity     BIGINT        NOT NULL,
    avg_price    DECIMAL(19,4) NOT NULL,
    realized_pnl DECIMAL(19,4) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id, symbol),
    CONSTRAINT fk_holdings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- `amount` is signed: negative for a buy, positive for a sell.
CREATE TABLE cash_transactions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    order_id      BIGINT        NULL,
    type          VARCHAR(10)   NOT NULL,
    amount        DECIMAL(19,4) NOT NULL,
    balance_after DECIMAL(19,4) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cash_user_created (user_id, created_at),
    CONSTRAINT fk_cash_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cash_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
) ENGINE = InnoDB;
