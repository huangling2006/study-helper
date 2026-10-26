package com.hl.study.user.service;

import com.hl.study.user.dto.LoginDTO;
import com.hl.study.user.dto.RegisterDTO;
import com.hl.study.user.vo.LoginVO;

public interface UserService {
    Long register(RegisterDTO dto);
    LoginVO login(LoginDTO dto);
    void logout(Long userId);
}