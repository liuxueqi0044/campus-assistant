-- M01.03 owns school membership, each verification attempt, and its append-only decision history.
-- Expiry is evaluated against expires_at and the server Clock; no scheduled status rewrite is required.

CREATE TABLE campus_verification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    school_id BIGINT UNSIGNED NOT NULL,
    campus_id BIGINT UNSIGNED NOT NULL,
    method VARCHAR(32) NOT NULL,
    evidence_refs_json JSON NOT NULL,
    proof_reference VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    submitted_at DATETIME(3) NOT NULL,
    reviewed_at DATETIME(3) NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    review_reason VARCHAR(500) NULL,
    valid_from DATETIME(3) NULL,
    expires_at DATETIME(3) NULL,
    pending_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_verification_pending (user_id, school_id, pending_slot),
    KEY idx_campus_verification_queue (school_id, status, submitted_at, id),
    KEY idx_campus_verification_user (user_id, school_id, submitted_at, id),
    CONSTRAINT fk_campus_verification_school FOREIGN KEY (school_id)
        REFERENCES campus_school (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campus_verification_scope FOREIGN KEY (school_id, campus_id)
        REFERENCES campus_campus (school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_verification_method CHECK (CHAR_LENGTH(method) > 0),
    CONSTRAINT chk_campus_verification_evidence CHECK (JSON_TYPE(evidence_refs_json) = 'ARRAY'),
    CONSTRAINT chk_campus_verification_proof CHECK (CHAR_LENGTH(proof_reference) > 0),
    CONSTRAINT chk_campus_verification_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED')),
    CONSTRAINT chk_campus_verification_version CHECK (version > 0),
    CONSTRAINT chk_campus_verification_review_shape CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by IS NULL AND review_reason IS NULL)
        OR (status <> 'PENDING' AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL
            AND review_reason IS NOT NULL AND CHAR_LENGTH(review_reason) > 0)
    ),
    CONSTRAINT chk_campus_verification_validity CHECK (
        (status IN ('APPROVED', 'REVOKED') AND valid_from IS NOT NULL AND expires_at IS NOT NULL
            AND expires_at > valid_from)
        OR (status IN ('PENDING', 'REJECTED') AND valid_from IS NULL AND expires_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_membership (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    school_id BIGINT UNSIGNED NOT NULL,
    campus_id BIGINT UNSIGNED NOT NULL,
    source_verification_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    scope_version BIGINT UNSIGNED NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoked_by BIGINT UNSIGNED NULL,
    revocation_reason VARCHAR(500) NULL,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_membership_user_school (user_id, school_id),
    KEY idx_campus_membership_scope (school_id, campus_id, status, expires_at),
    KEY idx_campus_membership_verification (source_verification_id),
    CONSTRAINT fk_campus_membership_school FOREIGN KEY (school_id)
        REFERENCES campus_school (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campus_membership_scope FOREIGN KEY (school_id, campus_id)
        REFERENCES campus_campus (school_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_campus_membership_verification FOREIGN KEY (source_verification_id)
        REFERENCES campus_verification (id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_membership_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_campus_membership_validity CHECK (expires_at > effective_from),
    CONSTRAINT chk_campus_membership_scope_version CHECK (scope_version > 0),
    CONSTRAINT chk_campus_membership_revocation_shape CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revoked_by IS NULL AND revocation_reason IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL
            AND revocation_reason IS NOT NULL AND CHAR_LENGTH(revocation_reason) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_verification_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    verification_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(500) NOT NULL,
    verification_version BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_verification_review_version (verification_id, verification_version),
    KEY idx_campus_verification_review_operator (operator_id, occurred_at, id),
    CONSTRAINT fk_campus_verification_review_application FOREIGN KEY (verification_id)
        REFERENCES campus_verification (id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_verification_review_transition CHECK (
        (action = 'APPROVE' AND from_status = 'PENDING' AND to_status = 'APPROVED')
        OR (action = 'REJECT' AND from_status = 'PENDING' AND to_status = 'REJECTED')
        OR (action = 'REVOKE' AND from_status = 'APPROVED' AND to_status = 'REVOKED')
    ),
    CONSTRAINT chk_campus_verification_review_version CHECK (verification_version > 1),
    CONSTRAINT chk_campus_verification_review_reason CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
