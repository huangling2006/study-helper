package com.hl.study.interceptor;

import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.context.UserContext;
import com.hl.study.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String REDIS_TOKEN_KEY = "login:token:";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String token = resolveToken(req);
        if (token == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 关键：JWT本身无法主动失效，靠Redis是否存在来判断是否已登出
        String redisToken = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY + userId);
        if (redisToken == null || !redisToken.equals(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UserContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear(); // 必须清理，线程复用会导致用户信息串号
    }

    private String resolveToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length());
        }
        // 根据 2026.10 修正版：只认 Authorization 头，不认 URL 传参，防止 token 进 Nginx 日志
        return null;
    }
}