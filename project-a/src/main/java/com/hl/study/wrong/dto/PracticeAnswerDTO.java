package com.hl.study.wrong.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PracticeAnswerDTO {
    @NotNull(message = "题目ID不能为空")
    @Positive(message = "题目ID必须大于0")
    private Long questionId;

    @NotBlank(message = "用户答案不能为空")
    @Pattern(regexp = "(?i)[A-D]", message = "答案只能是 A/B/C/D")
    private String userAnswer;
}
