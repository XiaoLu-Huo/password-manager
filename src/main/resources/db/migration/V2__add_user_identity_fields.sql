ALTER TABLE pm_user
    ADD COLUMN username VARCHAR(64) NOT NULL DEFAULT '' COMMENT '用户名',
    ADD COLUMN email VARCHAR(256) NOT NULL DEFAULT '' COMMENT '邮箱',
    ADD UNIQUE INDEX uk_pm_user_username (username),
    ADD UNIQUE INDEX uk_pm_user_email (email);
