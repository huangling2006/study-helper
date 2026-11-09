package com.hl.study.interceptor;

import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.context.UserContext;
import com.hl.study.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    // 【修复点】：补上你在 UserServiceImpl 里用到的常量
    public static final String REDIS_TOKEN_KEY = "login:token:";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        token = token.substring(7);

        // 2. 解析 JWT
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 3. 校验 Redis 中的 Token（防主动登出）
        try {
            // 使用常量拼接 key
            String redisToken = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY + userId);
            if (redisToken == null || !redisToken.equals(token)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        } catch (RedisConnectionFailureException e) {
            // 【W5 容错】：如果 Redis 彻底宕机，为了保住核心业务，降级为“仅校验 JWT 签名”
            log.warn("Redis is down, fallback to stateless JWT check, userId={}", userId);
        }

        // 4. 放入 ThreadLocal
        UserContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}