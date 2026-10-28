USE study_helper;

-- 1. 创建测试专用表
DROP TABLE IF EXISTS question_idx_test;
CREATE TABLE question_idx_test LIKE question;

-- 2. 灌入基础数据（排除自增主键 id）
INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question;

-- 3. 疯狂翻倍扩容至约6400条（都排除 id）
INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question_idx_test;

INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question_idx_test;

INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question_idx_test;

INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question_idx_test;

INSERT INTO question_idx_test (subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
SELECT subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis FROM question_idx_test;

SELECT COUNT(*) AS test_rows FROM question_idx_test;

-- ================= 状态A：无索引 =================
SELECT '【状态A】无索引' AS stage;
ALTER TABLE question_idx_test DROP INDEX idx_subject_chapter, DROP INDEX idx_subject_chapter_difficulty;

EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' AND difficulty = 2 ORDER BY id ASC LIMIT 10 OFFSET 0;
EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' ORDER BY id ASC LIMIT 10 OFFSET 0;

-- ================= 状态B：仅 idx_subject_chapter =================
SELECT '【状态B】仅 subject_chapter 索引' AS stage;
ALTER TABLE question_idx_test ADD INDEX idx_subject_chapter(subject, chapter);

EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' AND difficulty = 2 ORDER BY id ASC LIMIT 10 OFFSET 0;
EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' ORDER BY id ASC LIMIT 10 OFFSET 0;

-- ================= 状态C：加三列联合索引 =================
SELECT '【状态C】加三列联合索引' AS stage;
ALTER TABLE question_idx_test ADD INDEX idx_subject_chapter_difficulty(subject, chapter, difficulty);

EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' AND difficulty = 2 ORDER BY id ASC LIMIT 10 OFFSET 0;
EXPLAIN SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' ORDER BY id ASC LIMIT 10 OFFSET 0;