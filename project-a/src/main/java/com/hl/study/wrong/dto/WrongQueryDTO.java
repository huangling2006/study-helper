package com.hl.study.wrong.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WrongQueryDTO {
    @Min(value = 1, message = "page 最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 50, message = "size 最大为 50")
    private Integer size = 10;

    @Size(max = 64, message = "subject 最长 64 个字符")
    private String subject;

    /**
     * 白名单校验：只允许这两个值，如果前端乱传会被全局异常处理器拦截返回 400
     */
    @Pattern(regexp = "lastWrongTime|wrongCount", message = "orderBy 只支持 lastWrongTime 或 wrongCount")
    private String orderBy;
}
