package com.hl.study.stat.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TodayStatVO {
    private Long todayTotal;
    private Long todayCorrect;
    private Double todayAccuracy;
    private Boolean todayAnswered;
    private Integer streak;
    private Integer lastStreak;
    private String date;
}
