-- Slice 1: accounts and their virtual cash, plus the per-user risk limits that
-- slice 3 will read before every order.

CREATE TABLE users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    cash_balance  DECIMAL(19,4) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

CREATE TABLE risk_settings (
    user_id               BIGINT        NOT NULL,
    max_position_pct      DECIMAL(5,2)  NOT NULL,
    max_order_value       DECIMAL(19,4) NOT NULL,
    default_stop_loss_pct DECIMAL(5,2)  NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_risk_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
