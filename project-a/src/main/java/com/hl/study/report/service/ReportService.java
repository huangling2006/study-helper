package com.hl.study.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.mq.MqConst;
import com.hl.study.mq.ReportMessage;
import com.hl.study.report.ai.ReportStats;
import com.hl.study.report.ai.TemplateReportGenerator;
import com.hl.study.report.dto.ReportHistoryQueryDTO;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.mapper.ReportMapper;
import com.hl.study.report.vo.ReportHistoryVO;
import com.hl.study.report.vo.ReportStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
    private TemplateReportGenerator templateReportGenerator;

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

        // 3. 投递消息 (带 CorrelationData 用于异步回调确认)
        try {
            ReportMessage msg = new ReportMessage(report.getId(), userId, requestId);

            CorrelationData cd = new CorrelationData(requestId);

            rabbitTemplate.convertAndSend(MqConst.EXCHANGE, MqConst.RK_GENERATE, msg, m -> {
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                m.getMessageProperties().setMessageId(requestId);
                return m;
            }, cd);

            // 异步 confirm 回调，投递失败则本地降级
            cd.getFuture().whenComplete((confirm, ex) -> {
                if (ex != null || confirm == null || !confirm.isAck()) {
                    log.error("publish not confirmed, degrade locally, requestId={}", requestId, ex);
                    degradeLocally(report.getId(), "publish_failed");
                }
            });

        } catch (Exception e) {
            // 同步抛出异常时（如连接断开），不再返回 500，直接降级返回 200
            log.error("publish report failed, degrade locally, requestId={}", requestId, e);
            stringRedisTemplate.delete(limitKey); // 投递失败，删掉限流key
            degradeLocally(report.getId(), "publish_failed");

            // 直接返回降级后的结果，status=2
            ReportStatusVO vo = new ReportStatusVO();
            vo.setRequestId(requestId);
            vo.setStatus(2);
            vo.setAiSuccess(false);
            return vo;
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

        // 【M1 修复】：补全时间字段
        vo.setCreateTime(r.getCreateTime());
        vo.setFinishTime(r.getFinishTime());

        return vo;
    }

    // ================= S1 新增：历史报告列表 =================
    public Page<ReportHistoryVO> listHistory(Long userId, ReportHistoryQueryDTO dto) {
        Page<ReportHistoryVO> page = new Page<>(dto.getPage(), dto.getSize());
        long offset = (long) (dto.getPage() - 1) * dto.getSize();

        List<ReportHistoryVO> records = reportMapper.selectHistory(userId, offset, dto.getSize());
        for (ReportHistoryVO vo : records) {
            vo.setAiSuccess(vo.getStatus() != null && vo.getStatus() == 1);
            vo.setDegraded(vo.getStatus() != null && vo.getStatus() == 2);
        }

        page.setRecords(records);
        page.setTotal(reportMapper.countHistory(userId));
        return page;
    }

    // ================= S4 新增：本地降级逻辑 =================
    private void degradeLocally(Long reportId, String reason) {
        // 【M2 修复】：使用真实的 TemplateReportGenerator 替代手写假 JSON
        // 构造一个空统计对象作为兜底，避免 NPE
        ReportStats emptyStats = new ReportStats(
                0, 0, 0.0, "平稳",
                Collections.emptyList(),
                Collections.emptyList()
        );

        // 生成带真实格式的降级模板
        String templateContent = templateReportGenerator.build(emptyStats, reason);

        // 更新数据库：状态置为 2，写入模板内容
        int updated = reportMapper.markSuccessOrDegraded(
                reportId, 2, templateContent, 0, "template-v1", reason);

        if (updated > 0) {
            log.info("degraded report generated successfully, reportId={}", reportId);
        } else {
            log.warn("degrade skipped, report status already changed, reportId={}", reportId);
        }
    }
}