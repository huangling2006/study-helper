package com.hl.study.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.common.R;
import com.hl.study.context.CurrentUser;
import com.hl.study.report.dto.ReportHistoryQueryDTO;
import com.hl.study.report.service.ReportService;
import com.hl.study.report.service.ReportStreamService;
import com.hl.study.report.vo.ReportHistoryVO;
import com.hl.study.report.vo.ReportStatusVO;
import com.hl.study.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/report")
@Tag(name = "AI学情报告")
public class ReportController {

    @Resource
    private ReportService reportService;

    // 注入 SSE 服务
    @Resource
    private ReportStreamService streamService;

    @PostMapping("/generate")
    @Operation(summary = "触发学情报告生成（异步）")
    public R<ReportStatusVO> generate(@CurrentUser User user) {
        return R.ok(reportService.trigger(user.getId()));
    }

    // ================= 【S1 新增】历史报告列表 =================
    // 注意：这个端点必须放在 /{requestId} 前面，否则 Spring 会把 "history" 当作 requestId 匹配
    @GetMapping("/history")
    @Operation(summary = "历史报告列表（摘要，只能查自己）")
    public R<Page<ReportHistoryVO>> history(@Valid ReportHistoryQueryDTO dto, @CurrentUser User user) {
        return R.ok(reportService.listHistory(user.getId(), dto));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询报告状态与内容（只能查自己）")
    public R<ReportStatusVO> get(@PathVariable String requestId, @CurrentUser User user) {
        return R.ok(reportService.getByRequestId(requestId, user.getId()));
    }

    @GetMapping(value = "/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "报告分段推送（SSE）")
    public SseEmitter stream(@PathVariable String requestId, @CurrentUser User user) {
        // 1. 必须在建立流之前校验归属，否则抛出异常时状态码改不了，变成了一堆 SSE 错误事件
        ReportStatusVO snapshot = reportService.getByRequestId(requestId, user.getId());

        // 2. 设置 5 分钟硬超时
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 3. 先发一条 status 事件，告诉前端当前进度
        try {
            emitter.send(SseEmitter.event().name("status").data("{\"status\":" + snapshot.getStatus() + "}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        // 4. 交给 Service 处理轮询推送
        streamService.attach(requestId, snapshot, emitter);

        return emitter;
    }
}