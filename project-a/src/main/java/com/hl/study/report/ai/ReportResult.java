package com.hl.study.report.ai;

public record ReportResult(
        int status,
        String contentJson,
        int tokens,
        String model,
        String errorMsg
) {
    public static ReportResult degraded(String content, String errorMsg, String model) {
        return new ReportResult(2, content, 0, model, errorMsg);
    }
    public static ReportResult ai(String content, int tokens, String model) {
        return new ReportResult(1, content, tokens, model, null);
    }
}