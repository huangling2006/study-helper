package com.hl.study.report.controller;

import com.hl.study.common.R;
import com.hl.study.context.CurrentUser;
import com.hl.study.report.service.ReportService;
import com.hl.study.report.vo.ReportStatusVO;
import com.hl.study.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@Tag(name = "AI学情报告")
public class ReportController {

    @Resource
    private ReportService reportService;

    @PostMapping("/generate")
    @Operation(summary = "触发学情报告生成（异步）")
    public R<ReportStatusVO> generate(@CurrentUser User user) {
        return R.ok(reportService.trigger(user.getId()));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "查询报告状态与内容（只能查自己）")
    public R<ReportStatusVO> get(@PathVariable String requestId, @CurrentUser User user) {
        return R.ok(reportService.getByRequestId(requestId, user.getId()));
    }
}
