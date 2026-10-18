-- Test-only M00.03 mechanism probe. It is not packaged in the production artifact and must not
-- be reused by a business domain. All actual business tables remain owned by their module.

CREATE TABLE campus_m00_03_transaction_example (
    id BIGINT NOT NULL,
    business_key VARCHAR(64) NOT NULL,
    amount_fen BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_m003_tx_example_business_key (business_key),
    KEY idx_campus_m003_tx_example_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'M00.03 transaction rollback probe';

CREATE TABLE campus_m00_03_transaction_example_outbox (
    id BIGINT NOT NULL,
    transaction_example_id BIGINT NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_m003_tx_outbox_event_key (event_key),
    KEY idx_campus_m003_tx_outbox_example_id (transaction_example_id),
    CONSTRAINT fk_campus_m003_tx_outbox_example
        FOREIGN KEY (transaction_example_id) REFERENCES campus_m00_03_transaction_example (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'M00.03 transaction rollback probe outbox stub';
