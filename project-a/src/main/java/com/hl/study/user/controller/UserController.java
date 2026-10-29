package com.hl.study.user.controller;

import com.hl.study.common.R;
import com.hl.study.context.CurrentUser;
import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.dto.UpdateUserDTO;
import com.hl.study.user.entity.User;
import com.hl.study.user.service.UserService;
import com.hl.study.user.vo.LoginVO;
import com.hl.study.user.vo.UserVO;
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
    public R<Void> logout(@CurrentUser User user) {
        userService.logout(user.getId());
        return R.ok();
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public R<UserVO> info(@CurrentUser User user) {
        return R.ok(userService.getInfo(user.getId()));
    }

    @PutMapping("/info")
    @Operation(summary = "修改个人信息")
    public R<Void> update(@CurrentUser User user, @Valid @RequestBody UpdateUserDTO dto) {
        userService.updateInfo(user.getId(), dto);
        return R.ok();
    }
}