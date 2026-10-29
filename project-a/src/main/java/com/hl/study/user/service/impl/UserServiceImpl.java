package com.hl.study.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.interceptor.LoginInterceptor;
import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.dto.UpdateUserDTO;
import com.hl.study.user.entity.User;
import com.hl.study.user.mapper.UserMapper;
import com.hl.study.user.service.UserService;
import com.hl.study.user.vo.LoginVO;
import com.hl.study.user.vo.UserVO;
import com.hl.study.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // --- 新增：BCrypt 编码器，strength 设为 10 ---
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    @Override
    public Long register(RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());

        // --- 修改：注册时，直接使用 BCrypt 加密存入数据库 ---
        user.setPassword(ENCODER.encode(dto.getPassword()));

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

        // --- 修改：使用 matches 方法进行比对，兼容老密文 ---
        if (user == null || !matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // --- 新增：如果校验成功，且发现是老版本的 SHA-256 密文，立刻平滑升级为 BCrypt ---
        if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
            user.setPassword(ENCODER.encode(dto.getPassword()));
            updateById(user);
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

    @Override
    public UserVO getInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    public void updateInfo(Long userId, UpdateUserDTO dto) {
        if (dto.getNickname() == null && dto.getAvatar() == null) {
            return;
        }

        User user = new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        updateById(user);
    }

    // ==========================================
    // 下面是密码工具方法
    // ==========================================

    /**
     * 密码匹配核心逻辑（兼容新版 BCrypt 和旧版 SHA-256）
     */
    private boolean matches(String raw, String stored) {
        if (stored == null) return false;
        // 如果是 BCrypt 密文 (以 $2a$ 或 $2b$ 开头)
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$")) {
            return ENCODER.matches(raw, stored);
        }
        // 兼容老的 SHA-256 密文
        return sha256Hex(raw).equalsIgnoreCase(stored);
    }

    /**
     * 纯原生 SHA-256 加密（为了兼容老数据，W5 之后如果全量迁移完毕可以删掉）
     */
    private String sha256Hex(String password) {
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