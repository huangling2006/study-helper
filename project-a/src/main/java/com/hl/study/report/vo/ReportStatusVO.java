package com.hl.study.report.vo;

import lombok.Data;

@Data
public class ReportStatusVO {
    private String requestId;
    private Integer status;
    private boolean aiSuccess;
    private String content;
    private Integer tokenCost;

    public ReportStatusVO() {}

    public ReportStatusVO(String requestId, Integer status, boolean aiSuccess, String content, Integer tokenCost) {
        this.requestId = requestId;
        this.status = status;
        this.aiSuccess = aiSuccess;
        this.content = content;
        this.tokenCost = tokenCost;
    }
}