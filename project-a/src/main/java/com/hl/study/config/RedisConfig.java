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
}