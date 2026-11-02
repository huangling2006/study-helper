-- deploy/sql/04_wrong_book_index.sql
USE study_helper;

-- 1. 加 idx_user_time（支撑列表按最近错时间倒序）
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'wrong_book'
                AND index_name = 'idx_user_time');
SET @ddl := IF(@exists = 0,
               'ALTER TABLE wrong_book ADD KEY idx_user_time (user_id, last_wrong_time)',
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 加 idx_question（支撑 Top100 的 GROUP BY question_id）
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'wrong_book'
                AND index_name = 'idx_question');
SET @ddl := IF(@exists = 0,
               'ALTER TABLE wrong_book ADD KEY idx_question (question_id)',
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 打印当前索引，确认添加成功
SHOW INDEX FROM wrong_book;