-- M01.02 owns only the public school/campus directory and an independent browse preference.
-- It deliberately does not create, reference, or mutate campus_membership.

CREATE TABLE campus_school (
    id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_version BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_school_code (code),
    KEY idx_campus_school_public (status, sort_order, id),
    CONSTRAINT chk_campus_school_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_campus_school_config_version CHECK (config_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_campus (
    id BIGINT UNSIGNED NOT NULL,
    school_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_version BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_campus_school_code (school_id, code),
    UNIQUE KEY uk_campus_campus_school_id (school_id, id),
    KEY idx_campus_campus_public (school_id, status, sort_order, id),
    CONSTRAINT fk_campus_campus_school FOREIGN KEY (school_id) REFERENCES campus_school (id) ON DELETE RESTRICT,
    CONSTRAINT chk_campus_campus_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_campus_campus_config_version CHECK (config_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campus_user_browse_preference (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    school_id BIGINT UNSIGNED NOT NULL,
    campus_id BIGINT UNSIGNED NOT NULL,
    creator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_browse_preference_user (user_id),
    KEY idx_campus_browse_preference_scope (school_id, campus_id),
    CONSTRAINT fk_campus_browse_preference_school FOREIGN KEY (school_id) REFERENCES campus_school (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campus_browse_preference_scope FOREIGN KEY (school_id, campus_id)
        REFERENCES campus_campus (school_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
