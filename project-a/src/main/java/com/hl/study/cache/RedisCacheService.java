package com.hl.study.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class RedisCacheService {

    public static final String NULL_SENTINEL = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofMinutes(5);

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DefaultRedisScript<Long> unlockScript;

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration baseTtl, Supplier<T> loader) {
        // 1. 查缓存
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return decode(cached, type, key);
        }

        // 2. 缓存未命中，抢锁防击穿
        String lockKey = key + ":lock";
        String token = UUID.randomUUID().toString();

        if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofSeconds(10)))) {
            try {
                // 3. 双重检查：抢锁期间可能已经有人回填了
                cached = stringRedisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return decode(cached, type, key);
                }

                // 4. 回源查库
                T value = loader.get();
                if (value == null) {
                    // 5. 防穿透：缓存空值哨兵
                    stringRedisTemplate.opsForValue().set(key, NULL_SENTINEL, NULL_TTL);
                    return null;
                }

                // 6. 防雪崩：TTL 抖动后写入
                stringRedisTemplate.opsForValue()
                        .set(key, encode(value), jitter(baseTtl, 20, Duration.ofMinutes(5)));
                return value;
            } finally {
                unlock(lockKey, token);
            }
        }

        // 没抢到锁：自旋 3 次 × 50ms，仍没有就降级回源
        for (int i = 0; i < 3; i++) {
            sleepQuietly(50);
            cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return decode(cached, type, key);
            }
        }
        log.warn("cache rebuild wait timeout, fallback to db, key={}", key);
        return loader.get();
    }

    public void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.delete(key);
                }
            });
        } else {
            stringRedisTemplate.delete(key);
        }
    }

    public void deleteWithDoubleDelete(String key) {
        stringRedisTemplate.delete(key);
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> stringRedisTemplate.delete(key));
    }

    public Duration jitter(Duration base, int percent, Duration cap) {
        long baseMs = base.toMillis();
        long span = Math.min(baseMs * percent / 100, cap.toMillis());
        long delta = ThreadLocalRandom.current().nextLong(-span, span + 1);
        return Duration.ofMillis(Math.max(1000, baseMs + delta));
    }

    private <T> T decode(String json, TypeReference<T> type, String key) {
        if (NULL_SENTINEL.equals(json)) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("cache decode failed, drop key={}", key, e);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cache encode failed", e);
        }
    }

    private void unlock(String lockKey, String token) {
        stringRedisTemplate.execute(unlockScript, List.of(lockKey), token);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}