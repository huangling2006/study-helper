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
            if (row == null || row.getStatus() != 0) {
                channel.basicAck(tag, false);
                return;
            }

            // 模拟查库聚合出的统计数据
            String statsJson = "{\"total\":34,\"correct\":19,\"accuracy\":0.5588,\"weakPoints\":[{\"subject\":\"Java\",\"chapter\":\"集合\",\"wrong\":6}],\"practiceQuestionIds\":[37,12]}";

            // 调用 AI 生成
            ReportResult result = aiService.generate(statsJson);

            // 落库
            row.setStatus(result.status());
            row.setContent(result.contentJson());
            row.setTokenCost(result.tokens());
            row.setModel(result.model());
            row.setErrorMsg(result.errorMsg());
            row.setFinishTime(LocalDateTime.now());
            reportMapper.updateById(row);

            channel.basicAck(tag, false);
            log.info("报告生成完毕，已 ACK: requestId={}, status={}", msg.requestId(), result.status());

        } catch (Exception e) {
            log.error("消费异常", e);
            channel.basicNack(tag, false, false);
        }
    }
}