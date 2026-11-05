package com.hl.study.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.common.TraceIdFilter;
import com.hl.study.mq.MqConst;
import com.hl.study.mq.ReportMessage;
import com.hl.study.report.ai.ReportStats;
import com.hl.study.report.ai.TemplateReportGenerator;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.mapper.ReportMapper;
import com.hl.study.report.vo.ReportStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class ReportService {

    @Resource
    private ReportMapper reportMapper;
    
    @Resource
    private RabbitTemplate rabbitTemplate;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ReportAggregator aggregator;

    @Resource
    private TemplateReportGenerator template;

    public ReportStatusVO trigger(Long userId) {
        // 1. 限流：SET NX EX 60，value 存 requestId
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String limitKey = "report:limit:" + userId;

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, requestId, Duration.ofSeconds(60));

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT); // HTTP 429
        }

        // 2. 先落库：status=0
        AiReport report = new AiReport();
        report.setUserId(userId);
        report.setRequestId(requestId);
        report.setStatus(0);
        reportMapper.insert(report);

        // 3. 投递消息 (包含 confirm 回调与本地降级)
        try {
            publish(new ReportMessage(report.getId(), userId, requestId));
        } catch (Exception e) {
            // 同步抛异常（如连接不上 RabbitMQ）
            log.error("publish report failed immediately, degrade locally, requestId={}", requestId, e);
            stringRedisTemplate.delete(limitKey);
            degradeLocally(report.getId(), "publish_failed");
            return new ReportStatusVO(requestId, 2, false, null, 0, null, null);
        }

        // 4. 正常返回 status=0 (生成中)
        return new ReportStatusVO(requestId, 0, false, null, 0, null, null);
    }

    private void publish(ReportMessage msg) {
        CorrelationData cd = new CorrelationData(msg.requestId());
        
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE, MqConst.RK_GENERATE, msg, m -> {
            MessageProperties p = m.getMessageProperties();
            p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            p.setMessageId(msg.requestId());
            p.setContentType("application/json");
            
            // S7: traceId 透传，写入消息头
            String traceId = MDC.get(TraceIdFilter.TRACE_ID);
            if (traceId != null) {
                p.setHeader(TraceIdFilter.TRACE_ID, traceId);
            }
            return m;
        }, cd);

        // S6: 异步 confirm 回调，若未 ACK 则执行本地降级
        cd.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null || confirm == null || !confirm.isAck()) {
                log.error("publish not confirmed, degrade locally, requestId={}", msg.requestId(), ex);
                degradeLocally(msg.reportId(), "publish_failed");
            }
        });
    }

    /**
     * S6: 投递失败时的本地降级
     */
    public void degradeLocally(Long reportId, String reason) {
        AiReport row = reportMapper.selectById(reportId);
        if (row == null || row.getStatus() != 0) {
            return;
        }
        
        ReportStats stats = aggregator.collect(row.getUserId());
        String content = template.build(stats, reason);
        
        // 更新状态为 2 (降级完成)，模型标为 template-v1
        reportMapper.markSuccessOrDegraded(reportId, 2, content, 0, "template-v1", reason);
        log.info("report degraded locally, reportId={}, reason={}", reportId, reason);
    }

    public ReportStatusVO getByRequestId(String requestId, Long userId) {
        AiReport r = reportMapper.selectOne(Wrappers.<AiReport>lambdaQuery()
                .eq(AiReport::getRequestId, requestId)
                .eq(AiReport::getUserId, userId));
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ReportStatusVO vo = new ReportStatusVO();
        vo.setRequestId(r.getRequestId());
        vo.setStatus(r.getStatus());
        vo.setAiSuccess(r.getStatus() == 1);
        vo.setContent(r.getContent());
        vo.setTokenCost(r.getTokenCost());
        vo.setCreateTime(r.getCreateTime());
        vo.setFinishTime(r.getFinishTime());
        return vo;
    }
}