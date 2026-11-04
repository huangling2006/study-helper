USE study_helper;
CREATE TABLE IF NOT EXISTS ai_report (
                                         id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                         user_id BIGINT NOT NULL COMMENT '触发用户',
                                         request_id CHAR(32) NOT NULL COMMENT 'UUID去横线，幂等键',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0生成中 1AI成功 2降级完成',
    content MEDIUMTEXT NULL COMMENT '结构化JSON报告',
    token_cost INT NOT NULL DEFAULT 0 COMMENT '本次调用总token',
    model VARCHAR(64) NULL COMMENT 'deepseek-chat / template-v1',
    error_msg VARCHAR(255) NULL COMMENT '降级或失败原因摘要',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_user_time (user_id, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 学情诊断报告';