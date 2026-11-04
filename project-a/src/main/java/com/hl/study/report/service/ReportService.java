package com.hl.study.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.mq.MqConst;
import com.hl.study.mq.ReportMessage;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.mapper.ReportMapper;
import com.hl.study.report.vo.ReportStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
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

        // 3. 投递消息
        try {
            ReportMessage msg = new ReportMessage(report.getId(), userId, requestId);
            rabbitTemplate.convertAndSend(MqConst.EXCHANGE, MqConst.RK_GENERATE, msg, m -> {
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                m.getMessageProperties().setMessageId(requestId);
                return m;
            });
        } catch (Exception e) {
            log.error("publish report failed, degrade locally, requestId={}", requestId, e);
            stringRedisTemplate.delete(limitKey); // 投递失败，删掉限流key
            // 这里先不做本地降级的具体逻辑，后面补，直接抛出异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "投递失败");
        }

        ReportStatusVO vo = new ReportStatusVO();
        vo.setRequestId(requestId);
        vo.setStatus(0);
        return vo;
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
        return vo;
    }
}