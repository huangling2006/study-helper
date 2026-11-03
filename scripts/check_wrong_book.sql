USE study_helper;

-- 1) 错题本是否出现重复行（uk_user_question 应保证返回空集）
SELECT user_id, question_id, COUNT(*) AS c
FROM wrong_book
GROUP BY user_id, question_id
HAVING c > 1;

-- 2) 答错但没进错题本的题（W2 的 upsert 漏掉的异常样本）
SELECT r.user_id, r.question_id
FROM user_answer_record r
         LEFT JOIN wrong_book w
                   ON w.user_id = r.user_id AND w.question_id = r.question_id
WHERE r.is_correct = 0 AND w.id IS NULL
    LIMIT 20;

-- 3) wrong_count 是否与答题记录里的实际错次一致
SELECT w.user_id, w.question_id, w.wrong_count,
       (SELECT COUNT(*) FROM user_answer_record r
        WHERE r.user_id = w.user_id
          AND r.question_id = w.question_id
          AND r.is_correct = 0) AS real_cnt
FROM wrong_book w
HAVING wrong_count <> real_cnt;

-- 4) Top100 的数据基础：全站错题次数分布 Top10
SELECT question_id, COUNT(*) AS wrong_times
FROM user_answer_record
WHERE is_correct = 0
GROUP BY question_id
ORDER BY wrong_times DESC
    LIMIT 10;