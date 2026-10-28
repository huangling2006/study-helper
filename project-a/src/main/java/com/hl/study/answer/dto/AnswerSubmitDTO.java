package com.hl.study.answer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AnswerSubmitDTO {
    @NotNull
    @Positive
    private Long questionId;
    @NotBlank
    @Pattern(regexp = "^[A-Da-d]$", message = "答案只能是A/B/C/D")
    private String userAnswer;
}