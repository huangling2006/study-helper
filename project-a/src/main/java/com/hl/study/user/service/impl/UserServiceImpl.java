package com.hl.study.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.interceptor.LoginInterceptor;
import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.entity.User;
import com.hl.study.user.mapper.UserMapper;
import com.hl.study.user.service.UserService;
import com.hl.study.user.vo.LoginVO;
import com.hl.study.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long register(RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        // 使用原生 SHA-256 进行密码加密
        user.setPassword(hashPassword(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : "新用户_" + dto.getUsername());
        user.setRole("USER");
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USER_NAME_EXIST);
        }
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        // 校验密码
        if (user == null || !hashPassword(dto.getPassword()).equals(user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole());

        stringRedisTemplate.opsForValue().set(
                LoginInterceptor.REDIS_TOKEN_KEY + user.getId(),
                token,
                Duration.ofMinutes(120)
        );

        return LoginVO.builder()
                .token(token).userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    @Override
    public void logout(Long userId) {
        stringRedisTemplate.delete(LoginInterceptor.REDIS_TOKEN_KEY + userId);
    }

    /**
     * 纯原生 SHA-256 加密，绝不依赖任何第三方包
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
}