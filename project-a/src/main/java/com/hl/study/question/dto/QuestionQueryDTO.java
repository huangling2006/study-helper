package com.hl.study.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class QuestionQueryDTO {
    private String subject;
    private String chapter;
    @Min(value = 1, message = "难度必须为1,2,3")
    @Max(value = 3, message = "难度必须为1,2,3")
    private Integer difficulty;
    private Integer page = 1;
    @Max(value = 50, message = "单页最大50条")
    private Integer size = 10;
}
