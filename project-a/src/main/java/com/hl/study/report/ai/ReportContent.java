package com.hl.study.report.ai;

import java.util.List;

public record ReportContent(
        String overall,
        List<WeakPoint> weakPoints,
        List<String> causes,
        List<String> suggestions,
        List<Long> practiceQuestionIds,
        boolean degraded
) {
    public record WeakPoint(String name, Integer wrongCount, Double wrongRate) {}
}
