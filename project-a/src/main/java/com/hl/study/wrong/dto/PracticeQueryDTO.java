package com.hl.study.wrong.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PracticeQueryDTO {
    @Min(1) @Max(20)
    private Integer size = 5;

    @Size(max = 64)
    private String subject;
}
