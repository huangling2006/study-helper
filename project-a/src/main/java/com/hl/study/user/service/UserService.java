package com.hl.study.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.dto.UpdateUserDTO;
import com.hl.study.user.entity.User;
import com.hl.study.user.vo.LoginVO;
import com.hl.study.user.vo.UserVO;

public interface UserService extends IService<User> {

    Long register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    void logout(Long userId);

    // --- 本次 S1 任务新增的方法 ---
    UserVO getInfo(Long userId);

    void updateInfo(Long userId, UpdateUserDTO dto);
}