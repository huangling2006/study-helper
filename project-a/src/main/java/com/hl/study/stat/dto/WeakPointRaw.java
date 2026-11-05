package com.hl.study.stat.dto;

import lombok.Data;

@Data
public class WeakPointRaw {
    private String subject;
    private String chapter;
    private Long total;
    private Long wrong;
    private Double wrongRate;
}