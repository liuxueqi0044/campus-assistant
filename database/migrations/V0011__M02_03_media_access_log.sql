-- M02.03 keeps short-lived delivery grants server-side and records private access decisions.
-- Neither table may contain the presented delivery token, object key, URL, signature or file content.

ALTER TABLE campus_verification
    ADD UNIQUE KEY uk_campus_verification_proof_reference (proof_reference);

CREATE TABLE campus_media_delivery_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    asset_id BIGINT UNSIGNED NOT NULL,
    asset_generation BIGINT UNSIGNED NOT NULL,
    asset_version BIGINT UNSIGNED NOT NULL,
    binding_id BIGINT UNSIGNED NOT NULL,
    case_reference VARCHAR(128) NULL,
    reason VARCHAR(500) NULL,
    expires_at DATETIME(3) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_media_delivery_grant_token (token_hash),
    KEY idx_campus_media_delivery_grant_actor (actor_type, actor_id, expires_at, id),
    KEY idx_campus_media_delivery_grant_asset (asset_id, expires_at, id),
    CONSTRAINT chk_campus_media_delivery_grant_token
        CHECK (token_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_campus_media_delivery_grant_actor_type
        CHECK (actor_type IN ('MEMBER', 'OPERATOR')),
    CONSTRAINT chk_campus_media_delivery_grant_actor CHECK (actor_id > 0),
    CONSTRAINT chk_campus_media_delivery_grant_asset CHECK (asset_id > 0),
    CONSTRAINT chk_campus_media_delivery_grant_generation CHECK (asset_generation > 0),
    CONSTRAINT chk_campus_media_delivery_grant_asset_version CHECK (asset_version > 0),
    CONSTRAINT chk_campus_media_delivery_grant_binding CHECK (binding_id > 0),
    CONSTRAINT chk_campus_media_delivery_grant_actor_context CHECK (
        (actor_type = 'MEMBER' AND case_reference IS NULL AND reason IS NULL)
        OR (actor_type = 'OPERATOR' AND case_reference IS NOT NULL AND reason IS NOT NULL)
    ),
    CONSTRAINT chk_campus_media_delivery_grant_expiry CHECK (expires_at > create_time),
    CONSTRAINT chk_campus_media_delivery_grant_version CHECK (version > 0),
    CONSTRAINT fk_campus_media_delivery_grant_asset
        FOREIGN KEY (asset_id) REFERENCES campus_media_asset (id),
    CONSTRAINT fk_campus_media_delivery_grant_binding
        FOREIGN KEY (binding_id) REFERENCES campus_media_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_media_access_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    asset_id BIGINT UNSIGNED NULL,
    asset_generation BIGINT UNSIGNED NULL,
    asset_version BIGINT UNSIGNED NULL,
    binding_id BIGINT UNSIGNED NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(128) NULL,
    action VARCHAR(32) NOT NULL,
    purpose VARCHAR(48) NULL,
    decision VARCHAR(8) NOT NULL,
    case_reference VARCHAR(128) NULL,
    reason VARCHAR(500) NULL,
    audit_labels_json JSON NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_campus_media_access_log_actor (actor_type, actor_id, occurred_at, id),
    KEY idx_campus_media_access_log_asset (asset_id, occurred_at, id),
    KEY idx_campus_media_access_log_resource (resource_type, resource_id, occurred_at, id),
    KEY idx_campus_media_access_log_trace (trace_id, id),
    CONSTRAINT chk_campus_media_access_log_actor_type
        CHECK (actor_type IN ('MEMBER', 'OPERATOR')),
    CONSTRAINT chk_campus_media_access_log_actor CHECK (actor_id > 0),
    CONSTRAINT chk_campus_media_access_log_asset CHECK (asset_id IS NULL OR asset_id > 0),
    CONSTRAINT chk_campus_media_access_log_generation
        CHECK (asset_generation IS NULL OR asset_generation > 0),
    CONSTRAINT chk_campus_media_access_log_asset_version
        CHECK (asset_version IS NULL OR asset_version > 0),
    CONSTRAINT chk_campus_media_access_log_binding CHECK (binding_id IS NULL OR binding_id > 0),
    CONSTRAINT chk_campus_media_access_log_action
        CHECK (action IN ('ISSUE_DELIVERY', 'OPEN_AUTHORIZED')),
    CONSTRAINT chk_campus_media_access_log_decision CHECK (decision IN ('ALLOW', 'DENY')),
    CONSTRAINT chk_campus_media_access_log_resource_pair CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR (resource_type IS NOT NULL AND resource_id IS NOT NULL)
    ),
    CONSTRAINT chk_campus_media_access_log_asset_facts CHECK (
        (asset_id IS NULL AND asset_generation IS NULL AND asset_version IS NULL)
        OR (asset_id IS NOT NULL AND asset_generation IS NULL AND asset_version IS NULL)
        OR (asset_id IS NOT NULL AND asset_generation IS NOT NULL AND asset_version IS NOT NULL)
    ),
    CONSTRAINT chk_campus_media_access_log_operator_context CHECK (
        (actor_type = 'MEMBER' AND case_reference IS NULL AND reason IS NULL)
        OR (actor_type = 'OPERATOR' AND case_reference IS NOT NULL AND reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
