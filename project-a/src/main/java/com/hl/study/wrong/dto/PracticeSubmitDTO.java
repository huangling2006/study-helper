package com.hl.study.wrong.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class PracticeSubmitDTO {
    @NotEmpty
    @Size(max = 20)
    @Valid
    private List<PracticeAnswerDTO> answers;
}
