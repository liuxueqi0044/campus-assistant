-- M01.06 extends, but never copies, the upstream member account.
-- An absent row means the upstream-enabled account is ACTIVE with lifecycle/auth version zero.

CREATE TABLE campus_account_lifecycle (
    user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL,
    auth_version BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    status_reason VARCHAR(500) NOT NULL,
    closure_requested_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    session_revocation_state VARCHAR(16) NOT NULL,
    last_transition_actor_type VARCHAR(16) NOT NULL,
    last_transition_by BIGINT UNSIGNED NULL,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (user_id),
    KEY idx_campus_account_lifecycle_state (status, update_time, user_id),
    KEY idx_campus_account_lifecycle_revocation (session_revocation_state, update_time, user_id),
    CONSTRAINT chk_campus_account_lifecycle_status CHECK (
        status IN ('ACTIVE', 'LIMITED', 'SUSPENDED', 'CLOSURE_PENDING', 'CLOSED')
    ),
    CONSTRAINT chk_campus_account_lifecycle_versions CHECK (auth_version > 0 AND version > 0),
    CONSTRAINT chk_campus_account_lifecycle_reason CHECK (CHAR_LENGTH(status_reason) > 0),
    CONSTRAINT chk_campus_account_lifecycle_revocation CHECK (
        session_revocation_state IN ('PENDING', 'COMPLETE')
    ),
    CONSTRAINT chk_campus_account_lifecycle_actor CHECK (
        last_transition_actor_type IN ('MEMBER', 'OPERATOR', 'SYSTEM')
        AND (last_transition_actor_type = 'SYSTEM' OR last_transition_by IS NOT NULL)
    ),
    CONSTRAINT chk_campus_account_lifecycle_closure CHECK (
        (status IN ('ACTIVE', 'LIMITED', 'SUSPENDED')
            AND closure_requested_at IS NULL AND closed_at IS NULL)
        OR (status = 'CLOSURE_PENDING' AND closure_requested_at IS NOT NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closure_requested_at IS NOT NULL AND closed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_account_lifecycle_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    lifecycle_version BIGINT UNSIGNED NOT NULL,
    auth_version BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_account_lifecycle_log_version (user_id, lifecycle_version),
    KEY idx_campus_account_lifecycle_log_actor (actor_id, occurred_at, id),
    CONSTRAINT fk_campus_account_lifecycle_log_account FOREIGN KEY (user_id)
        REFERENCES campus_account_lifecycle (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_account_lifecycle_log_status CHECK (
        from_status IN ('IMPLICIT_ACTIVE', 'ACTIVE', 'LIMITED', 'SUSPENDED', 'CLOSURE_PENDING')
        AND to_status IN ('ACTIVE', 'LIMITED', 'SUSPENDED', 'CLOSURE_PENDING', 'CLOSED')
        AND from_status <> to_status
    ),
    CONSTRAINT chk_campus_account_lifecycle_log_reason CHECK (CHAR_LENGTH(reason) > 0),
    CONSTRAINT chk_campus_account_lifecycle_log_actor CHECK (
        actor_type IN ('MEMBER', 'OPERATOR', 'SYSTEM')
        AND (actor_type = 'SYSTEM' OR actor_id IS NOT NULL)
    ),
    CONSTRAINT chk_campus_account_lifecycle_log_versions CHECK (
        lifecycle_version > 0 AND auth_version > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
