package com.hl.study.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDTO {
    @Size(max = 64, message = "昵称最长 64 个字符")
    private String nickname;

    @Size(max = 255, message = "头像 URL 最长 255 个字符")
    private String avatar;
}