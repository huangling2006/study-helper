package com.hl.study.stat.dto;

import lombok.Data;

@Data
public class SubjectStat {
    private String subject;
    private Long total;
    private Long correct;
}