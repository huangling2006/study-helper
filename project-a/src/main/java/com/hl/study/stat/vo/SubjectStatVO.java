package com.hl.study.stat.vo;

import lombok.Data;

@Data
public class SubjectStatVO {
    private String subject;
    private Long total;
    private Long correct;
    private Double accuracy;
}