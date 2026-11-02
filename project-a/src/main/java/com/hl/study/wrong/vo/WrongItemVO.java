package com.hl.study.wrong.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WrongItemVO {
    private Long wrongId;
    private Long questionId;
    private String subject;
    private String chapter;
    private String content;
    private Integer difficulty;
    private Integer wrongCount;
    private LocalDateTime lastWrongTime;
    // 绝对不要加 answer 和 analysis
}