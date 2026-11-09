package com.hl.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    /**
     * 解锁 Lua 脚本：防止锁过期后误删别人的锁
     */
    @Bean
    public DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 统计每日答题计数 Lua 脚本：
     * 原子性保证 HINCRBY t、HINCRBY c 和设置过期时间同时完成
     * KEYS[1]: 统计的 Hash Key
     * ARGV[1]: 是否答对（"1" 或 "0"）
     * ARGV[2]: 过期时间戳（次日 02:00 的 epoch 秒数）
     */
    @Bean
    public DefaultRedisScript<Long> statIncrScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local t = redis.call('HINCRBY', KEYS[1], 't', 1)
            redis.call('HINCRBY', KEYS[1], 'c', ARGV[1])
            if redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIREAT', KEYS[1], ARGV[2])
            end
            return t
        """);
        script.setResultType(Long.class);
        return script;
    }
}