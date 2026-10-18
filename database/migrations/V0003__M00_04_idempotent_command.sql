-- M00.04 durable command reservations. The result is a semantic replay cache only;
-- business tables keep their own uniqueness constraints and are not owned here.
CREATE TABLE campus_idempotency (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    platform_tenant_id BIGINT UNSIGNED NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(128) NOT NULL,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    processing_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    business_reference VARCHAR(128) NULL,
    result_json LONGTEXT NULL,
    result_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    expires_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_idempotency_identity (platform_tenant_id, actor_id, action, key_hash),
    KEY idx_campus_idempotency_expiry (status, expires_at),
    CONSTRAINT chk_campus_idempotency_status CHECK (status IN ('PROCESSING', 'SUCCEEDED')),
    CONSTRAINT chk_campus_idempotency_result_version CHECK (result_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
