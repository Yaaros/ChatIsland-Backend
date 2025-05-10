-- VIP变动记录表
CREATE TABLE IF NOT EXISTS vip_change_record (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 uid VARCHAR(36) NOT NULL,
                                                 change_type ENUM('grant', 'revoke', 'auto_expired') NOT NULL,
                                                 start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 end_time TIMESTAMP NULL,
                                                 reason VARCHAR(255) NOT NULL,
                                                 active BOOLEAN NOT NULL DEFAULT TRUE,
                                                 INDEX idx_uid (uid),
                                                 INDEX idx_active (active)
);

-- 模型调用记录表
CREATE TABLE IF NOT EXISTS model_invocation_record (
                                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                       uid VARCHAR(36) NOT NULL,
                                                       content TEXT NOT NULL,
                                                       model_name VARCHAR(100) NOT NULL,
                                                       invocation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       tokens_used INT NOT NULL DEFAULT 0,
                                                       success BOOLEAN NOT NULL DEFAULT TRUE,
                                                       FOREIGN KEY (uid) REFERENCES user(uid) ON DELETE CASCADE,
                                                       INDEX idx_uid_time (uid, invocation_time)
);

-- 在user表中添加外键关联（如果需要）
ALTER TABLE user ADD INDEX idx_uid (uid);