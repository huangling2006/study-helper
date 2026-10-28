package com.hl.study.answer.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnswerResultVO {
    private Long questionId;
    private Boolean isCorrect;
    private String correctAnswer;
    private String analysis;
    private Boolean wrongBookAdded;
}
