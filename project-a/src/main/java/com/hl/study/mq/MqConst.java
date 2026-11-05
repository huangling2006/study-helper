package com.hl.study.mq;

/**
 * RabbitMQ 常量集中管理
 */
public final class MqConst {

    private MqConst() {
    }

    public static final String EXCHANGE = "study.report.exchange";
    public static final String QUEUE_MAIN = "study.report.queue";
    public static final String QUEUE_RETRY = "study.report.retry.queue";
    public static final String QUEUE_DLQ = "study.report.dlq";

    public static final String RK_GENERATE = "report.generate";
    public static final String RK_RETRY = "report.retry";
    public static final String RK_DLQ = "report.dlq";

    // ===== S5 新增：两层重试相关常量 =====
    public static final long RETRY_TTL = 15_000L; // 重试队列 TTL 15 秒
    public static final int MAX_RETRY = 3;        // 最大重试次数
}