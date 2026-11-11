package com.hl.study.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportHistoryVO {
    private String requestId;
    private Integer status;
    private Boolean aiSuccess;
    private Boolean degraded;
    private Integer weakPointCount;
    private String overview;
    private Integer tokenCost;
    private String model;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}