-- Slice 7: the symbols a user follows. `symbol` carries no foreign key to `stocks`,
-- for the same reason orders do not: that table only holds symbols someone searched.

CREATE TABLE watchlist (
    user_id    BIGINT      NOT NULL,
    symbol     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, symbol),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
