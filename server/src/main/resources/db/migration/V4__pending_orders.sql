-- Slice 6: limit and stop-loss orders. A pending order waits in `orders` until the
-- scheduler fills it or the user cancels it. `attach_stop_loss` asks for a stop-loss
-- to be created from the fill price once a buy fills.

ALTER TABLE orders
    ADD COLUMN limit_price      DECIMAL(19,4) NULL AFTER quantity,
    ADD COLUMN trigger_price    DECIMAL(19,4) NULL AFTER limit_price,
    ADD COLUMN attach_stop_loss BOOLEAN       NOT NULL DEFAULT FALSE AFTER trigger_price,
    ADD KEY idx_orders_status (status);
