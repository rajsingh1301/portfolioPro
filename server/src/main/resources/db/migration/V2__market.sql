-- Slice 2: the symbols the app knows about. Rows arrive from Finnhub search, so the
-- table is a local catalogue of what users have looked up, not the whole US market.
-- Slice 3 references `symbol` from orders, trades and holdings.

CREATE TABLE stocks (
    symbol     VARCHAR(20)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    exchange   VARCHAR(50)  NULL,
    sector     VARCHAR(100) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (symbol)
) ENGINE = InnoDB;
