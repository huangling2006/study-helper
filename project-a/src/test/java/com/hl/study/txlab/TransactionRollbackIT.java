package com.hl.study.txlab;

import com.hl.study.answer.dto.AnswerSubmitDTO;
import com.hl.study.answer.service.AnswerService;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 组 5：答题事务原子性（W6 实测，对应 09 文档 §7.5 / 14 条验收第 8 条）。
 *
 * 三个方法对应三种情况：
 *   submitCommitsAll            正常提交：答题记录 +1、错题本 +1、报告不动
 *   runtimeExceptionRollsBack   事务内抛异常：三表一行都不许动（用数据库触发器注入失败，不改业务代码）
 *   swallowedExceptionStillCommits  调用方吞掉异常：记录仍在，证明事务边界在 submit 方法上，方法返回即已提交
 *
 * 固定临时用户 test_tx_999999，跑完把答题记录、错题、触发器、Redis key 全部清掉。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.ai.deepseek.api-key=test-placeholder-key"
        })
class TransactionRollbackIT {

    private static final String USERNAME = "test_tx_999999";

    @Autowired
    private AnswerService answerService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private long userId;
    private long questionId;
    private String correctAnswer;
    private String statKey;
    private String checkinKey;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO user(username, password, nickname, role, status) VALUES (?, 'x', 'tx', 'USER', 1) "
                + "ON DUPLICATE KEY UPDATE nickname = 'tx'", USERNAME);
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);

        questionId = jdbc.queryForObject("SELECT id FROM question ORDER BY id LIMIT 1", Long.class);
        correctAnswer = jdbc.queryForObject("SELECT answer FROM question WHERE id = ?", String.class, questionId);

        LocalDate today = LocalDate.now();
        statKey = "stat:answer:" + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        checkinKey = "stat:checkin:" + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        clean();
    }

    @AfterEach
    void tearDown() {
        dropFailTrigger();
        clean();
        jdbc.update("DELETE FROM user WHERE id = ?", userId);
    }

    @Test
    void submitCommitsAll() {
        long[] before = counts();
        answerService.submit(userId, dto(wrongAnswer()));
        long[] after = counts();

        assertEquals(before[0] + 1, after[0], "答题记录应 +1");
        assertEquals(before[1] + 1, after[1], "答错后错题本应 +1");
        assertEquals(before[2], after[2], "事务不应碰 ai_report");
    }

    @Test
    void runtimeExceptionRollsBack() {
        createFailTrigger();
        long[] before = counts();

        assertThrows(Exception.class, () -> answerService.submit(userId, dto(wrongAnswer())),
                "错题写入失败时 submit 必须抛异常");

        long[] after = counts();
        assertArrayEquals(before, after, "事务内异常必须让三表一动不动");
        dropFailTrigger();
    }

    @Test
    void swallowedExceptionStillCommits() {
        long[] before = counts();
        answerService.submit(userId, dto(wrongAnswer()));

        // 方法返回后再抛，异常已被调用方吞掉：这时事务早就提交了
        try {
            throw new RuntimeException("caller-side failure after submit returned");
        } catch (RuntimeException ignored) {
            // 故意吞掉
        }

        long[] after = counts();
        assertEquals(before[0] + 1, after[0], "事务边界在 submit 方法上，方法返回即已提交");
        assertEquals(before[1] + 1, after[1], "错题本同理");
    }

    // ---------------- 内部工具 ----------------

    private long[] counts() {
        long a = jdbc.queryForObject("SELECT COUNT(*) FROM user_answer_record WHERE user_id = ?", Long.class, userId);
        long b = jdbc.queryForObject("SELECT COUNT(*) FROM wrong_book WHERE user_id = ?", Long.class, userId);
        long c = jdbc.queryForObject("SELECT COUNT(*) FROM ai_report WHERE user_id = ?", Long.class, userId);
        return new long[]{a, b, c};
    }

    private String wrongAnswer() {
        for (String s : List.of("A", "B", "C", "D")) {
            if (!s.equalsIgnoreCase(correctAnswer)) {
                return s;
            }
        }
        return "A";
    }

    private AnswerSubmitDTO dto(String answer) {
        AnswerSubmitDTO d = new AnswerSubmitDTO();
        d.setQuestionId(questionId);
        d.setUserAnswer(answer);
        return d;
    }

    private void clean() {
        jdbc.update("DELETE FROM user_answer_record WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM wrong_book WHERE user_id = ?", userId);
        redis.delete(statKey);
        redis.delete(checkinKey);
    }

    private void createFailTrigger() {
        dropFailTrigger();
        jdbc.execute("CREATE TRIGGER trg_tx_fail BEFORE INSERT ON wrong_book FOR EACH ROW "
                + "BEGIN IF NEW.user_id = " + userId + " THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'w6 injected failure'; "
                + "END IF; END");
    }

    private void dropFailTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_tx_fail");
    }
}
