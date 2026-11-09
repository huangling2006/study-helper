package com.hl.study.stat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class TrendVO {
    private Integer days;
    private List<Point> points;

    @Data
    @AllArgsConstructor
    public static class Point {
        private String date;
        private Long total;
        private Long correct;
        private Double accuracy;
    }
}