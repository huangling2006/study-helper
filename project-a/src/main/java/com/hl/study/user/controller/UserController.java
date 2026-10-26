package com.hl.study.user.controller;

import com.hl.study.common.R;
import com.hl.study.context.UserContext;
import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.service.UserService;
import com.hl.study.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户模块")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public R<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(userService.register(dto));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(userService.login(dto));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public R<Void> logout() {
        Long userId = UserContext.get();
        userService.logout(userId);
        return R.ok();
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public R<String> info() {
        Long userId = UserContext.get();
        return R.ok("当前登录用户 ID 是: " + userId);
    }
}