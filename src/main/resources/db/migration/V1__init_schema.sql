-- =============================================
-- Password Manager - 初始化数据库 Schema
-- =============================================

-- pm_user 用户表
CREATE TABLE pm_user (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_password_hash     VARCHAR(512)  NOT NULL COMMENT 'Argon2id 哈希值',
    salt                     VARCHAR(128)  NOT NULL COMMENT '哈希盐值',
    encryption_key_encrypted BLOB          NOT NULL COMMENT '加密后的 DEK',
    failed_attempts          INT           NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    locked_until             DATETIME      NULL COMMENT '锁定截止时间',
    auto_lock_minutes        INT           NOT NULL DEFAULT 5 COMMENT '自动锁定超时',
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- pm_credential 凭证表
CREATE TABLE pm_credential (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT        NOT NULL COMMENT '用户 ID',
    account_name       VARCHAR(256)  NOT NULL COMMENT '账户名称',
    username           VARCHAR(256)  NOT NULL COMMENT '用户名',
    password_encrypted BLOB          NOT NULL COMMENT '密码（AES-256-GCM）',
    url                VARCHAR(1024) NULL COMMENT '关联 URL',
    notes              TEXT          NULL COMMENT '备注',
    tags               VARCHAR(512)  NULL COMMENT '分类标签',
    iv                 BLOB          NOT NULL COMMENT 'AES-GCM IV',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pm_credential_user_id (user_id),
    INDEX idx_pm_credential_tags (tags(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证表';

-- pm_password_history 密码历史表
CREATE TABLE pm_password_history (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    credential_id      BIGINT   NOT NULL COMMENT '凭证 ID',
    password_encrypted BLOB     NOT NULL COMMENT '旧密码（加密）',
    iv                 BLOB     NOT NULL COMMENT 'AES-GCM IV',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    INDEX idx_pm_password_history_credential_id (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码历史表';

-- pm_password_rule 密码规则表
CREATE TABLE pm_password_rule (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '用户 ID',
    rule_name         VARCHAR(128) NOT NULL COMMENT '规则名称',
    length            INT          NOT NULL DEFAULT 16 COMMENT '密码长度',
    include_uppercase TINYINT      NOT NULL DEFAULT 1 COMMENT '包含大写字母',
    include_lowercase TINYINT      NOT NULL DEFAULT 1 COMMENT '包含小写字母',
    include_digits    TINYINT      NOT NULL DEFAULT 1 COMMENT '包含数字',
    include_special   TINYINT      NOT NULL DEFAULT 1 COMMENT '包含特殊字符',
    is_default        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认规则',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pm_password_rule_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码规则表';

-- pm_mfa_config MFA 配置表
CREATE TABLE pm_mfa_config (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT       NOT NULL COMMENT '用户 ID',
    totp_secret_encrypted    VARCHAR(512) NOT NULL COMMENT 'TOTP 密钥（加密）',
    enabled                  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用',
    recovery_codes_encrypted TEXT         NULL COMMENT '恢复码（加密）',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_pm_mfa_config_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA 配置表';
