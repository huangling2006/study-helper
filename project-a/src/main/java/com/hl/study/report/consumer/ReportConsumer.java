package com.hl.study.report.consumer;

import com.hl.study.mq.MqConst;
import com.hl.study.mq.ReportMessage;
import com.hl.study.report.ai.ReportAiService;
import com.hl.study.report.ai.ReportResult;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.mapper.ReportMapper;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class ReportConsumer {

    @Resource
    private ReportMapper reportMapper;
    @Resource
    private ReportAiService aiService;

    @RabbitListener(queues = MqConst.QUEUE_MAIN, ackMode = "MANUAL")
    public void onMessage(ReportMessage msg, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        log.info("收到报告生成消息: requestId={}", msg.requestId());

        try {
            AiReport row = reportMapper.selectById(msg.reportId());

            // 幂等拦截：如果不是 status=0（生成中），说明已经被处理过，直接 ACK 丢弃
            if (row == null || row.getStatus() != 0) {
                log.info("skip duplicated report, requestId={}, status={}",
                        msg.requestId(), row == null ? "missing" : row.getStatus());
                channel.basicAck(tag, false);
                return;
            }

            // TODO: W5 将在这里接入真实的数据聚合 (ReportStats)
            // 目前暂时使用模拟数据
            String statsJson = "{\"total\":34,\"correct\":19,\"accuracy\":0.5588,\"weakPoints\":[{\"subject\":\"Java\",\"chapter\":\"集合\",\"wrong\":6}],\"practiceQuestionIds\":[37,12]}";

            // 调用 AI 生成 (包含三层降级逻辑)
            ReportResult result = aiService.generate(statsJson);

            // 落库，注意这里的 status 是 1(成功) 或 2(降级)
            row.setStatus(result.status());
            row.setContent(result.contentJson());
            row.setTokenCost(result.tokens());
            row.setModel(result.model());
            row.setErrorMsg(result.errorMsg());
            row.setFinishTime(LocalDateTime.now());

            // 这里的 updateById 建议在 W5 换成带条件的更新 (where status = 0)
            // 以应对极端并发，不过目前已经有了 selectById 判断，先跑通即可
            reportMapper.updateById(row);

            channel.basicAck(tag, false);
            log.info("报告生成完毕，已 ACK: requestId={}, status={}, tokens={}",
                    msg.requestId(), result.status(), result.tokens());

        } catch (Exception e) {
            log.error("消费异常，准备进入重试或死信", e);
            // requeue = false，防止无限重试死循环
            channel.basicNack(tag, false, false);
        }
    }
}