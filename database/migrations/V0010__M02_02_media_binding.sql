-- M02.02 owns verified object facts and exclusive business binding.
-- Client-declared metadata remains on campus_media_asset for comparison only.

ALTER TABLE campus_media_asset
    DROP CHECK chk_campus_media_asset_status,
    ADD COLUMN actual_mime VARCHAR(127) NULL AFTER declared_size,
    ADD COLUMN actual_size BIGINT UNSIGNED NULL AFTER actual_mime,
    ADD COLUMN content_sha256 CHAR(64) NULL AFTER actual_size,
    ADD COLUMN validated_generation BIGINT UNSIGNED NULL AFTER content_sha256,
    ADD COLUMN validated_at DATETIME(3) NULL AFTER validated_generation,
    ADD COLUMN validation_failure VARCHAR(64) NULL AFTER validated_at,
    ADD CONSTRAINT chk_campus_media_asset_status
        CHECK (status IN ('UPLOADING', 'VALIDATING', 'VALID', 'REJECTED')),
    ADD CONSTRAINT chk_campus_media_asset_actual_size
        CHECK (actual_size IS NULL OR actual_size >= 0),
    ADD CONSTRAINT chk_campus_media_asset_validated_generation
        CHECK (validated_generation IS NULL OR validated_generation > 0),
    ADD CONSTRAINT chk_campus_media_asset_validation_facts
        CHECK (
            (status IN ('UPLOADING', 'VALIDATING')
                AND actual_mime IS NULL
                AND actual_size IS NULL
                AND content_sha256 IS NULL
                AND validated_generation IS NULL
                AND validated_at IS NULL
                AND validation_failure IS NULL)
            OR
            (status = 'VALID'
                AND actual_mime IS NOT NULL
                AND actual_size IS NOT NULL
                AND content_sha256 IS NOT NULL
                AND validated_generation = generation
                AND validated_at IS NOT NULL
                AND validation_failure IS NULL)
            OR
            (status = 'REJECTED'
                AND validated_generation = generation
                AND validated_at IS NOT NULL
                AND validation_failure IS NOT NULL)
        );

CREATE TABLE campus_media_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    asset_id BIGINT UNSIGNED NOT NULL,
    asset_generation BIGINT UNSIGNED NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(48) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    context_hash CHAR(64) NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    creator VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_media_binding_asset (asset_id),
    UNIQUE KEY uk_campus_media_binding_resource_asset (resource_type, resource_id, asset_id),
    KEY idx_campus_media_binding_resource (resource_type, resource_id, id),
    KEY idx_campus_media_binding_owner (owner_id, purpose, create_time, id),
    CONSTRAINT chk_campus_media_binding_asset CHECK (asset_id > 0),
    CONSTRAINT chk_campus_media_binding_generation CHECK (asset_generation > 0),
    CONSTRAINT chk_campus_media_binding_owner CHECK (owner_id > 0),
    CONSTRAINT chk_campus_media_binding_resource_type CHECK (CHAR_LENGTH(TRIM(resource_type)) > 0),
    CONSTRAINT chk_campus_media_binding_resource_id CHECK (CHAR_LENGTH(TRIM(resource_id)) > 0),
    CONSTRAINT chk_campus_media_binding_context_hash CHECK (context_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_campus_media_binding_window CHECK (expires_at > valid_from),
    CONSTRAINT chk_campus_media_binding_version CHECK (version > 0),
    CONSTRAINT fk_campus_media_binding_asset
        FOREIGN KEY (asset_id) REFERENCES campus_media_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
