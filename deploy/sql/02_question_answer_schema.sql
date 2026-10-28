USE study_helper;

CREATE TABLE IF NOT EXISTS question (
                                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目主键',
                                        subject VARCHAR(64) NOT NULL COMMENT '科目',
    chapter VARCHAR(128) NOT NULL COMMENT '章节',
    content TEXT NOT NULL COMMENT '题干',
    option_a VARCHAR(500) NOT NULL,
    option_b VARCHAR(500) NOT NULL,
    option_c VARCHAR(500) NOT NULL,
    option_d VARCHAR(500) NOT NULL,
    answer CHAR(1) NOT NULL COMMENT 'A/B/C/D',
    difficulty TINYINT NOT NULL COMMENT '1简单 2中等 3困难',
    analysis TEXT NULL COMMENT '题目解析',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_subject_chapter (subject, chapter),
    KEY idx_subject_chapter_difficulty (subject, chapter, difficulty)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

CREATE TABLE IF NOT EXISTS user_answer_record (
                                                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题记录主键',
                                                  user_id BIGINT NOT NULL COMMENT '答题用户',
                                                  question_id BIGINT NOT NULL COMMENT '题目 id',
                                                  user_answer CHAR(1) NOT NULL COMMENT '用户答案 A/B/C/D',
    is_correct TINYINT(1) NOT NULL COMMENT '是否答对',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, create_time),
    KEY idx_user_question (user_id, question_id),
    KEY idx_question_correct (question_id, is_correct)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户答题记录';