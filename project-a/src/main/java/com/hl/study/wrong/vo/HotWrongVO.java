package com.hl.study.wrong.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HotWrongVO {
    private Long questionId;
    private String subject;
    private String chapter;
    private String content;
    private Integer wrongCount;
    private LocalDateTime lastWrongTime;
}