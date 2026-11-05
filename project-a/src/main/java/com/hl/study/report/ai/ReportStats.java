package com.hl.study.report.ai;

import java.util.List;

/**
 * W4 欠账：喂给 AI / 模板报告的真实统计摘要。
 * 所有字段均由 SQL 聚合而来，禁止硬编码。
 */
public record ReportStats(
        long total,
        long correct,
        double accuracy,
        String trend,
        List<WeakPoint> weakPoints,
        List<Long> practiceQuestionIds
) {
    public record WeakPoint(String subject, String chapter, long total, long wrong, double wrongRate) {}
}