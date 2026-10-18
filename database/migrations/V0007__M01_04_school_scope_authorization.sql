-- M01.04 owns explicit administrator grants layered on top of upstream RBAC.
-- PLATFORM is a real scope type; NULL school/campus values never imply global access.

CREATE TABLE campus_operator_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_id BIGINT UNSIGNED NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    school_id BIGINT UNSIGNED NULL,
    campus_id BIGINT UNSIGNED NULL,
    actions_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    granted_by BIGINT UNSIGNED NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoked_by BIGINT UNSIGNED NULL,
    revocation_reason VARCHAR(500) NULL,
    scope_school_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(school_id, 0)) STORED,
    scope_campus_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(campus_id, 0)) STORED,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_operator_grant_scope (
        operator_id, scope_type, scope_school_key, scope_campus_key
    ),
    KEY idx_campus_operator_grant_authorize (operator_id, status, expires_at, id),
    KEY idx_campus_operator_grant_school (school_id, campus_id, status, id),
    CONSTRAINT fk_campus_operator_grant_school FOREIGN KEY (school_id)
        REFERENCES campus_school (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campus_operator_grant_campus FOREIGN KEY (school_id, campus_id)
        REFERENCES campus_campus (school_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_operator_grant_scope CHECK (
        (scope_type = 'PLATFORM' AND school_id IS NULL AND campus_id IS NULL)
        OR (scope_type = 'SCHOOL' AND school_id IS NOT NULL AND campus_id IS NULL)
        OR (scope_type = 'CAMPUS' AND school_id IS NOT NULL AND campus_id IS NOT NULL)
    ),
    CONSTRAINT chk_campus_operator_grant_actions CHECK (
        JSON_TYPE(actions_json) = 'ARRAY' AND JSON_LENGTH(actions_json) > 0
    ),
    CONSTRAINT chk_campus_operator_grant_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_campus_operator_grant_validity CHECK (expires_at > valid_from),
    CONSTRAINT chk_campus_operator_grant_version CHECK (version > 0),
    CONSTRAINT chk_campus_operator_grant_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revoked_by IS NULL AND revocation_reason IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL
            AND revocation_reason IS NOT NULL AND CHAR_LENGTH(revocation_reason) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_operator_grant_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    grant_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(16) NOT NULL,
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(500) NOT NULL,
    grant_version BIGINT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_operator_grant_audit_version (grant_id, grant_version),
    KEY idx_campus_operator_grant_audit_operator (operator_id, occurred_at, id),
    CONSTRAINT fk_campus_operator_grant_audit_grant FOREIGN KEY (grant_id)
        REFERENCES campus_operator_grant (id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_operator_grant_audit_action CHECK (
        (action = 'GRANT' AND from_status = 'NONE' AND to_status = 'ACTIVE')
        OR (action = 'UPDATE' AND from_status = 'ACTIVE' AND to_status = 'ACTIVE')
        OR (action = 'RESTORE' AND from_status = 'REVOKED' AND to_status = 'ACTIVE')
        OR (action = 'REVOKE' AND from_status = 'ACTIVE' AND to_status = 'REVOKED')
    ),
    CONSTRAINT chk_campus_operator_grant_audit_reason CHECK (CHAR_LENGTH(reason) > 0),
    CONSTRAINT chk_campus_operator_grant_audit_version CHECK (grant_version > 0),
    CONSTRAINT chk_campus_operator_grant_audit_snapshot CHECK (JSON_TYPE(snapshot_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_operator_authorization_version (
    operator_id BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (operator_id),
    CONSTRAINT chk_campus_operator_authorization_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
