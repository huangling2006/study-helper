USE study_helper;

CREATE TABLE IF NOT EXISTS wrong_book (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          user_id BIGINT NOT NULL,
                                          question_id BIGINT NOT NULL,
                                          wrong_count INT NOT NULL DEFAULT 1,
                                          last_wrong_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          add_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          PRIMARY KEY (id),
    UNIQUE KEY uk_user_question (user_id, question_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题本';