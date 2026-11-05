package com.hl.study.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatusVO {
    private String requestId;
    private Integer status;
    private Boolean aiSuccess;
    private String content;
    private Integer tokenCost;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}