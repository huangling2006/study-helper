-- 1) 昨日与今日的答题量（人工与 Redis 的 t 字段对照）
SELECT DATE(create_time) d, COUNT(*) total, SUM(is_correct) correct
FROM user_answer_record WHERE user_id = 2
                          AND create_time >= CURDATE() - INTERVAL 7 DAY
GROUP BY DATE(create_time) ORDER BY d DESC;

-- 2) 有答题记录但没有任何一题进入错题本的用户（说明判分链路断过）
SELECT r.user_id, COUNT(*) wrongs
FROM user_answer_record r LEFT JOIN wrong_book w
                                    ON w.user_id = r.user_id AND w.question_id = r.question_id
WHERE r.is_correct = 0 AND w.id IS NULL GROUP BY r.user_id;

-- 3) 同一秒多条报告（验证 ORDER BY 两段排序的必要性）
SELECT create_time, COUNT(*) c FROM ai_report GROUP BY create_time
HAVING c > 1;

-- 4) 近 30 天科目分布（与 GET /api/stat/subject 逐行对照）
SELECT q.subject, COUNT(*) total, SUM(r.is_correct) correct
FROM user_answer_record r JOIN question q ON q.id = r.question_id
WHERE r.user_id = 2 AND r.create_time >= NOW() - INTERVAL 30 DAY
GROUP BY q.subject ORDER BY total DESC;