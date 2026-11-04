package com.hl.study.mq;

public interface MqConst {
    String EXCHANGE = "study.report.exchange";
    String QUEUE_MAIN = "study.report.queue";
    String QUEUE_RETRY = "study.report.retry.queue";
    String QUEUE_DLQ = "study.report.dlq";

    String RK_GENERATE = "report.generate";
    String RK_RETRY = "report.retry";
    String RK_DLQ = "report.dlq";

    int MAX_RETRY = 3;
}