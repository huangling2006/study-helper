package com.hl.study.txlab;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W2 验收：亲手复现三种事务失效（内部调用 / 非 public / 吞异常）。
 *
 * 运行前提：MySQL(3307)、Redis(6379) 已启动；MQ 监听在测试环境关闭，不参与。
 * 用 RANDOM_PORT 起真实 Web 上下文：knife4j 的自动配置依赖 springdoc 的
 * SpringDocConfigProperties（只在 Web 上下文里创建），用 WebEnvironment.NONE
 * 会直接 ApplicationContext 加载失败。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // 测试不依赖 MQ：关掉监听容器，避免 broker 没起就整类失败
                "spring.rabbitmq.listener.simple.auto-startup=false",
                // 测试不会真的调 AI，给个占位 key 防止空值校验
                "spring.ai.deepseek.api-key=test-placeholder-key"
        })
@Import(TransactionFailureLabTest.TxLabService.class)
class TransactionFailureLabTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionFailureLabTest.class);

    @Autowired
    private TxLabService service;

    @Autowired
    private JdbcTemplate jdbc;

    // 实验A：同类内部调用
    @Test
    void experimentA_selfInvocation_shouldNotRollback() {
        long userId = firstUserId();
        long questionId = firstQuestionId();
        clean(userId, questionId);

        long beforeRecord = count("user_answer_record");
        long beforeWrong = count("wrong_book");

        assertThrows(RuntimeException.class,
                () -> service.selfInvocationOuter(userId, questionId));

        long afterRecord = count("user_answer_record");
        long afterWrong = count("wrong_book");

        log.info("实验A结果 beforeRecord={}, afterRecord={}, beforeWrong={}, afterWrong={}",
                beforeRecord, afterRecord, beforeWrong, afterWrong);

        // 预期：因为绕过了代理，事务没生效，数据已经插进去了（after = before + 1）
        assertEquals(beforeRecord + 1, afterRecord);
        assertEquals(beforeWrong + 1, afterWrong);

        clean(userId, questionId);
    }

    // 实验B：@Transactional 标在非 public 方法上，代理根本不会拦截
    @Test
    void experimentB_nonPublicMethod_shouldNotRollback() {
        long userId = firstUserId();
        long questionId = firstQuestionId();
        clean(userId, questionId);

        long beforeRecord = count("user_answer_record");
        long beforeWrong = count("wrong_book");

        assertThrows(RuntimeException.class,
                () -> service.nonPublicOuter(userId, questionId));

        long afterRecord = count("user_answer_record");
        long afterWrong = count("wrong_book");

        log.info("实验B结果 beforeRecord={}, afterRecord={}, beforeWrong={}, afterWrong={}",
                beforeRecord, afterRecord, beforeWrong, afterWrong);

        // 预期：private 方法无法被 CGLIB 子类覆盖，注解形同虚设，异常抛出但数据留下
        assertEquals(beforeRecord + 1, afterRecord);
        assertEquals(beforeWrong + 1, afterWrong);

        clean(userId, questionId);
    }

    // 实验C：异常被 try-catch 吞掉
    @Test
    void experimentC_swallowException_shouldCommit() {
        long userId = firstUserId();
        long questionId = firstQuestionId();
        clean(userId, questionId);

        long beforeRecord = count("user_answer_record");
        long beforeWrong = count("wrong_book");

        // 方法内部吞掉了异常，不会向外抛，因此这里不需要 assertThrows
        service.swallowOuter(userId, questionId);

        long afterRecord = count("user_answer_record");
        long afterWrong = count("wrong_book");

        log.info("实验C结果 beforeRecord={}, afterRecord={}, beforeWrong={}, afterWrong={}",
                beforeRecord, afterRecord, beforeWrong, afterWrong);

        // 预期：因为异常被吞了，方法正常结束，事务提交了（after = before + 1）
        assertEquals(beforeRecord + 1, afterRecord);
        assertEquals(beforeWrong + 1, afterWrong);

        clean(userId, questionId);
    }

    private long firstUserId() {
        return jdbc.queryForObject("SELECT id FROM user ORDER BY id LIMIT 1", Long.class);
    }

    private long firstQuestionId() {
        return jdbc.queryForObject("SELECT id FROM question ORDER BY id LIMIT 1", Long.class);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private void clean(long userId, long questionId) {
        jdbc.update("DELETE FROM user_answer_record WHERE user_id=? AND question_id=?", userId, questionId);
        jdbc.update("DELETE FROM wrong_book WHERE user_id=? AND question_id=?", userId, questionId);
    }

    @Service
    static class TxLabService {
        private static final Logger log = LoggerFactory.getLogger(TxLabService.class);
        @Autowired
        private JdbcTemplate jdbc;

        public void selfInvocationOuter(Long userId, Long questionId) {
            // 实验A：同类内部 this 调用，绕过 Spring 事务代理
            this.selfInvocationInner(userId, questionId);
        }

        @Transactional(rollbackFor = Exception.class)
        public void selfInvocationInner(Long userId, Long questionId) {
            insertAnswerAndWrong(userId, questionId);
            throw new RuntimeException("实验A: 内部调用不回滚测试");
        }

        public void nonPublicOuter(Long userId, Long questionId) {
            // 实验B：private 方法不在代理的拦截范围内，事务边界根本没建立
            this.nonPublicInner(userId, questionId);
        }

        @Transactional(rollbackFor = Exception.class)
        private void nonPublicInner(Long userId, Long questionId) {
            insertAnswerAndWrong(userId, questionId);
            throw new RuntimeException("实验B: 非 public 方法事务不生效测试");
        }

        @Transactional(rollbackFor = Exception.class)
        public void swallowOuter(Long userId, Long questionId) {
            insertAnswerAndWrong(userId, questionId);
            try {
                throw new RuntimeException("实验C: 吞异常测试");
            } catch (Exception e) {
                // 实验C：故意吞掉，不重新抛出
                log.warn("异常被吞掉，不重新抛出", e);
            }
        }

        private void insertAnswerAndWrong(Long userId, Long questionId) {
            jdbc.update("INSERT INTO user_answer_record(user_id, question_id, user_answer, is_correct) VALUES (?, ?, 'A', 0)", userId, questionId);
            jdbc.update("INSERT INTO wrong_book(user_id, question_id, wrong_count, last_wrong_time, add_time) VALUES (?, ?, 1, NOW(), NOW()) ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW()", userId, questionId);
        }
    }
}