package com.hl.study.interceptor;

import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.context.UserContext;
import com.hl.study.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

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
            String redisToken = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY + userId);
            if (redisToken == null || !redisToken.equals(token)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        } catch (DataAccessException e) {
            // 【S13 修复】：放宽到 DataAccessException，涵盖 Redis 连接失败和超时异常
            log.warn("Redis unavailable or timeout, fallback to stateless JWT check, userId={}", userId, e);
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