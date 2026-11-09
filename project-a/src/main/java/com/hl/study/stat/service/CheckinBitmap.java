package com.hl.study.stat.service;

import com.hl.study.cache.CacheKeys;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class CheckinBitmap {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 获取 BITFIELD 的 Lua 脚本，返回值设定为 Long
    private static final RedisScript<Long> BITFIELD_GET_SCRIPT =
            RedisScript.of("return redis.call('BITFIELD', KEYS[1], 'GET', 'u31', '0')", Long.class);

    private String key(Long userId, LocalDate day) {
        return CacheKeys.STAT_CHECKIN + userId + ":" + day.format(MONTH_FMT);
    }

    /**
     * 打卡（必须放在答题事务的 afterCommit 中调用）
     */
    public void markQuietly(Long userId, LocalDate day) {
        String key = key(userId, day);
        try {
            // offset 从 0 开始，1号是 0，31号是 30
            stringRedisTemplate.opsForValue().setBit(key, day.getDayOfMonth() - 1, true);
            // 保留 96 天（3个月）
            stringRedisTemplate.expire(key, Duration.ofDays(96));
        } catch (Exception e) {
            log.warn("Redis checkin mark failed, userId={}", userId, e);
        }
    }

    /**
     * 计算连续打卡天数
     */
    public int streak(Long userId, LocalDate startDay) {
        int n = 0;
        LocalDate d = startDay;
        while (n < 365) {
            if (!check(userId, d)) {
                break;
            }
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    /**
     * 检查某天是否打卡
     */
    private boolean check(Long userId, LocalDate day) {
        String key = key(userId, day);
        try {
            // 【修复点】：这里直接接收 Long，因为脚本指定了 Long.class
            Long res = stringRedisTemplate.execute(BITFIELD_GET_SCRIPT, List.of(key));
            if (res == null) {
                return false;
            }
            long bits = res;

            // 【核心坑点】：BITFIELD 把 offset 0 当作最高位 (MSB)。
            // 1号对应 bit 30，2号对应 bit 29... 31号对应 bit 0。
            // 所以掩码向左偏移 (31 - 日) 位。
            int offsetInMonth = 31 - day.getDayOfMonth();
            return (bits & (1L << offsetInMonth)) != 0;

        } catch (Exception e) {
            log.error("Redis BITFIELD failed, key={}", key, e);
            throw e; // 抛出异常，让 Service 层降级
        }
    }
}