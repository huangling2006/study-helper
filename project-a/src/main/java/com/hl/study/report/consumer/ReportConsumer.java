package com.hl.study.report.consumer;

import com.hl.study.common.TraceIdFilter;
import com.hl.study.mq.MqConst;
import com.hl.study.mq.ReportMessage;
import com.hl.study.report.ai.ReportAiService;
import com.hl.study.report.ai.ReportResult;
import com.hl.study.report.ai.ReportStats;
import com.hl.study.report.ai.TemplateReportGenerator;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.mapper.ReportMapper;
import com.hl.study.report.service.ReportAggregator;
import com.hl.study.report.service.ReportStreamService; // W5 新增导入
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class ReportConsumer {

    @Resource
    private ReportMapper reportMapper;

    @Resource
    private ReportAiService aiService;

    @Resource
    private ReportAggregator aggregator;

    @Resource
    private TemplateReportGenerator template;

    @Resource
    private RabbitTemplate rabbitTemplate;

    // W5 新增：注入 SSE 服务
    @Resource
    private ReportStreamService streamService;

    @RabbitListener(queues = MqConst.QUEUE_MAIN, ackMode = "MANUAL")
    public void onMessage(ReportMessage msg, Message raw, Channel channel,
                          @Header(name = "x-retry-count", required = false) Integer retryCount,
                          @Header(name = TraceIdFilter.TRACE_ID, required = false) String traceId) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();

        // S7: traceId 透传，消费端入 MDC
        MDC.put(TraceIdFilter.TRACE_ID, traceId == null ? msg.requestId() : traceId);

        try {
            AiReport row = reportMapper.selectById(msg.reportId());

            // 幂等拦截：如果不是 status=0（生成中），说明已经被处理过，直接 ACK 丢弃
            if (row == null || row.getStatus() != 0) {
                log.info("skip duplicated report, requestId={}, status={}",
                        msg.requestId(), row == null ? "missing" : row.getStatus());
                channel.basicAck(tag, false);
                return;
            }

            // S2: 接入真实的数据聚合 (ReportStats)
            ReportStats stats = aggregator.collect(row.getUserId());

            // 调用 AI 生成 (内部包含三层降级逻辑，永不抛出异常)
            ReportResult result = aiService.generate(stats);

            // S4: 带条件的更新，防并发重复写，返回影响行数
            int updated = reportMapper.markSuccessOrDegraded(
                    row.getId(), result.status(), result.contentJson(),
                    result.tokens(), result.model(), result.errorMsg());

            if (updated > 0) {
                log.info("report done, requestId={}, status={}, tokens={}, model={}",
                        msg.requestId(), result.status(), result.tokens(), result.model());

                // W5 新增：异步把报告推入 SSE 管道
                streamService.publish(row.getRequestId(), result.contentJson(), result.status());

            } else {
                log.warn("报告状态更新失败，可能已被其他消费者处理: requestId={}", msg.requestId());
            }

            // 业务成功，确认消息
            channel.basicAck(tag, false);

        } catch (Exception e) {
            // S5: 处理异常重试与死信队列
            int next = retryCount == null ? 1 : retryCount + 1;
            log.warn("report consume failed, requestId={}, retry={}", msg.requestId(), next, e);

            if (next <= MqConst.MAX_RETRY) {
                // 进重试队列
                republishToRetry(msg, next);
            } else {
                // 重试耗尽，写模板报告 + 投递死信
                try {
                    AiReport row = reportMapper.selectById(msg.reportId());
                    if (row != null && row.getStatus() == 0) {
                        ReportStats stats = aggregator.collect(row.getUserId());
                        String content = template.build(stats, "retry_exhausted");
                        reportMapper.markSuccessOrDegraded(row.getId(), 2, content, 0, "template-v1", "retry_exhausted: " + e.getMessage());
                    }
                    publishToDlq(msg, "retry_exhausted", e.getMessage());
                } catch (Exception ex) {
                    log.error("retry exhausted fallback also failed", ex);
                }
            }
            // 原消息必须 ACK，否则会无限 requeue
            channel.basicAck(tag, false);
        } finally {
            // S7: 线程池复用，必须清理 MDC
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    private void republishToRetry(ReportMessage msg, int next) {
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE, MqConst.RK_RETRY, msg, m -> {
            m.getMessageProperties().setHeader("x-retry-count", next);
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return m;
        });
    }

    private void publishToDlq(ReportMessage msg, String reason, String detail) {
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE, MqConst.RK_DLQ,
                Map.of("requestId", msg.requestId(), "userId", msg.userId(),
                        "reason", reason, "detail", detail == null ? "" : detail,
                        "at", java.time.LocalDateTime.now().toString()),
                m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return m;
                });
    }
}