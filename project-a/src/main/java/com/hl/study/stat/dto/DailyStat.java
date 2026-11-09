package com.hl.study.stat.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class DailyStat {
    private LocalDate day;
    private Long total;
    private Long correct;
}