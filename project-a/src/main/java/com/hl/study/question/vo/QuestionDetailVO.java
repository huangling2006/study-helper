package com.hl.study.question.vo;

import lombok.Data;

@Data
public class QuestionDetailVO {
    private Long id;
    private String subject;
    private String chapter;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private Integer difficulty;
}