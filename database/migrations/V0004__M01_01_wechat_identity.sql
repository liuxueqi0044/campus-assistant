-- M01.01 owns the durable mapping between a server-configured WeChat mini-program identity and member_user.
-- No code, session_key, access token or refresh token is ever persisted in this table.
CREATE TABLE campus_wechat_identity_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    app_id VARCHAR(128) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    union_id VARCHAR(128) NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    creator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_wechat_identity_app_open (app_id, open_id),
    KEY idx_campus_wechat_identity_member (member_id),
    UNIQUE KEY uk_campus_wechat_identity_app_union (app_id, union_id),
    CONSTRAINT chk_campus_wechat_identity_open_id CHECK (CHAR_LENGTH(open_id) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
