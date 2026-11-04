package com.hl.study.mq;

public record ReportMessage(Long reportId, Long userId, String requestId) {
}