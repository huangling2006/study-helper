package com.hl.study.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hl.study.cache.CacheKeys;
import com.hl.study.report.vo.ReportStatusVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ReportStreamService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    // Spring 启动时会自动注入这个调度器，不要自己 new Thread
    @Resource
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 生产端：报告落库后，拆成 5 段推入 Redis List
     */
    public void publish(String requestId, String contentJson, int status) {
        String listKey = CacheKeys.REPORT_STREAM + requestId;
        String doneKey = listKey + ":done";
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            for (String field : List.of("overall", "weakPoints", "causes", "suggestions", "practiceQuestionIds")) {
                JsonNode node = root.get(field);
                if (node != null) {
                    String seg = objectMapper.writeValueAsString(Map.of("type", field, "text", node.toString()));
                    stringRedisTemplate.opsForList().rightPush(listKey, seg);
                }
            }
            stringRedisTemplate.opsForValue().set(doneKey, String.valueOf(status), Duration.ofMinutes(10));
            stringRedisTemplate.expire(listKey, Duration.ofMinutes(10));
            log.info("SSE publish success, requestId={}", requestId);
        } catch (Exception e) {
            log.error("SSE publish failed, requestId={}", requestId, e);
        }
    }

    /**
     * 消费端：挂载 SseEmitter，每 800ms 轮询一次 Redis List
     */
    public void attach(String requestId, ReportStatusVO snapshot, SseEmitter emitter) {
        String listKey = CacheKeys.REPORT_STREAM + requestId;
        String doneKey = listKey + ":done";

        // 游标，记录当前推到了第几段
        AtomicInteger cursor = new AtomicInteger(0);

        // 如果报告已完成，但 Redis 缓存过期了（超过10分钟），直接从 MySQL 快照里现拆现推
        if (snapshot.getStatus() != 0 && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(listKey))) {
            publish(requestId, snapshot.getContent(), snapshot.getStatus());
        }

        // 【修复点】：Spring 6 中 scheduleWithFixedDelay 只有两个参数：Runnable 和 Duration
        ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                // 读取 List 中尚未推送的段
                List<String> segs = stringRedisTemplate.opsForList().range(listKey, cursor.get(), -1);
                if (segs != null) {
                    for (String seg : segs) {
                        emitter.send(SseEmitter.event().name("segment").data(seg));
                        cursor.incrementAndGet();
                    }
                }

                // 检查是否完成
                String done = stringRedisTemplate.opsForValue().get(doneKey);
                if (done != null) {
                    emitter.send(SseEmitter.event().name("done").data("{\"status\":" + done + "}"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.warn("SSE send failed, requestId={}", requestId, e);
                emitter.completeWithError(e);
            }
        }, Duration.ofMillis(800)); // 每 800 毫秒执行一次

        // 客户端断开或超时，取消定时任务
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> { task.cancel(true); emitter.complete(); });
        emitter.onError(e -> task.cancel(true));
    }
}