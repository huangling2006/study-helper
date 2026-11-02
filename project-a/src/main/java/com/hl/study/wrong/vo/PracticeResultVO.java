package com.hl.study.wrong.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PracticeResultVO {
    private int total;
    private int correct;
    private int wrong;
    private int skipped;
    private BigDecimal accuracy;
    private List<PracticeItemVO> details;

    public PracticeResultVO(int total, int correct, int wrong, int skipped, BigDecimal accuracy, List<PracticeItemVO> details) {
        this.total = total;
        this.correct = correct;
        this.wrong = wrong;
        this.skipped = skipped;
        this.accuracy = accuracy;
        this.details = details;
    }
}