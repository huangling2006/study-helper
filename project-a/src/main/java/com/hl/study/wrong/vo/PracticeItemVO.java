package com.hl.study.wrong.vo;

import lombok.Data;

@Data
public class PracticeItemVO {
    private Long questionId;
    private Boolean isCorrect;
    private String correctAnswer;
    private String status;
    private String analysis;

    public PracticeItemVO(Long questionId, Boolean isCorrect, String correctAnswer, String status, String analysis) {
        this.questionId = questionId;
        this.isCorrect = isCorrect;
        this.correctAnswer = correctAnswer;
        this.status = status;
        this.analysis = analysis;
    }
}