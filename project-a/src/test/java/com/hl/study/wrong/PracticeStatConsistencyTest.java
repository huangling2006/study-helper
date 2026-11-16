package com.hl.study.wrong;

import com.hl.study.wrong.dto.PracticeAnswerDTO;
import com.hl.study.wrong.dto.PracticeSubmitDTO;
import com.hl.study.wrong.service.WrongBookService;
import com.hl.study.wrong.vo.PracticeResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归测试：错题重练必须与单题答题走同一套统计口径（decisions.md D-37）。
 *
 * 这条用例是为了防住 W5 遗留的「对账 diff」复发：重练只写了 MySQL、
 * 不写 Redis 日统计与打卡位，导致 /api/stat/today 与 user_answer_record 长期不一致。
 *
 * 用临时用户跑，跑完把答题记录、错题、Redis key、打卡位全部清掉，不污染业务用户数据。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.ai.deepseek.api-key=test-placeholder-key"
        })
class PracticeStatConsistencyTest {

    @Autowired
    private WrongBookService wrongBookService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private long userId;
    private long questionId;
    private String statKey;
    private String checkinKey;
    private int dayOfMonth;

    @BeforeEach
    void setUp() {
        String username = "txtest_" + System.nanoTime();
        jdbc.update("INSERT INTO user(username, password, nickname, role, status) VALUES (?, 'x', 'tx', 'USER', 1)", username);
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);

        // 拿一道有正确答案的题
        questionId = jdbc.queryForObject("SELECT id FROM question ORDER BY id LIMIT 1", Long.class);

        LocalDate today = LocalDate.now();
        statKey = "stat:answer:" + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        checkinKey = "stat:checkin:" + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        dayOfMonth = today.getDayOfMonth();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM user_answer_record WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM wrong_book WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user WHERE id = ?", userId);
        redis.delete(statKey);
        redis.delete(checkinKey);
    }

    @Test
    void practiceSubmit_shouldWriteBothDbAndRedisStat() {
        String correct = jdbc.queryForObject("SELECT answer FROM question WHERE id = ?", String.class, questionId);
        String wrongAnswer = "A".equalsIgnoreCase(correct) ? "B" : "A";

        PracticeAnswerDTO answer = new PracticeAnswerDTO();
        answer.setQuestionId(questionId);
        answer.setUserAnswer(wrongAnswer);
        PracticeSubmitDTO dto = new PracticeSubmitDTO();
        dto.setAnswers(List.of(answer));

        PracticeResultVO result = wrongBookService.submitPractice(userId, dto);

        // 1. 判分与返回体口径：错一题 → total=1 correct=0 wrong=1 accuracy=0
        assertEquals(1, result.getTotal());
        assertEquals(0, result.getCorrect());
        assertEquals(1, result.getWrong());
        assertEquals(0, result.getAccuracy().doubleValue(), 0.0001);

        // 2. MySQL 留痕
        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answer_record WHERE user_id = ? AND question_id = ?",
                Long.class, userId, questionId);
        assertEquals(1L, rows);

        // 3. Redis 日统计必须同步（这是 W5 漏掉的那一步）
        Map<Object, Object> stat = redis.opsForHash().entries(statKey);
        assertNotNull(stat);
        assertEquals("1", String.valueOf(stat.get("t")), "重练后今日答题数应为 1");
        assertEquals("0", String.valueOf(stat.get("c")), "答错时今日正确数应为 0");

        // 4. 打卡位图也要置位（1 号是第 0 位）
        assertEquals(Boolean.TRUE, redis.opsForValue().getBit(checkinKey, dayOfMonth - 1),
                "重练当天应记为已打卡");
    }
}
