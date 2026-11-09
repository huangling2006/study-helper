package com.hl.study.stat.service;

import com.hl.study.cache.CacheKeys;
import com.hl.study.stat.dto.AnswerCount;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DailyStatCounter {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DefaultRedisScript<Long> statIncrScript;

    public String key(Long userId, LocalDate day) {
        return CacheKeys.STAT_TODAY + userId + ":" + day.format(DAY_FMT);
    }

    /**
     * 写入统计（必须放在答题事务的 afterCommit 中调用）
     */
    public void incrQuietly(Long userId, boolean isCorrect) {
        LocalDate today = LocalDate.now();
        String key = key(userId, today);

        // 次日 02:00 的绝对秒数
        long expireAt = today.plusDays(1).atTime(2, 0)
                .atZone(ZoneId.systemDefault()).toEpochSecond();

        try {
            stringRedisTemplate.execute(statIncrScript,
                    List.of(key),
                    isCorrect ? "1" : "0",
                    String.valueOf(expireAt));
        } catch (Exception e) {
            log.warn("Redis stat incr failed, userId={}", userId, e);
        }
    }

    /**
     * 查询某天的统计
     * @return null 表示 Redis 里没有这个 key（未命中），而不是 0
     */
    public AnswerCount read(Long userId, LocalDate day) {
        String key = key(userId, day);
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);

        if (map == null || map.isEmpty() || !map.containsKey("t")) {
            return null;
        }

        return new AnswerCount(
                Long.valueOf(map.get("t").toString()),
                Long.valueOf(map.get("c").toString())
        );
    }

    /**
     * 静默回填（查库后如果 Redis 没数据，回填进去）
     */
    public void backfillQuietly(Long userId, LocalDate day, AnswerCount c) {
        String key = key(userId, day);
        long expireAt = day.plusDays(1).atTime(2, 0)
                .atZone(ZoneId.systemDefault()).toEpochSecond();
        try {
            stringRedisTemplate.opsForHash().putAll(key,
                    Map.of("t", String.valueOf(c.getTotal()), "c", String.valueOf(c.getCorrect())));
            stringRedisTemplate.expireAt(key, java.time.Instant.ofEpochSecond(expireAt));
        } catch (Exception e) {
            log.warn("Redis stat backfill failed, userId={}", userId, e);
        }
    }
}