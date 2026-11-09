package com.hl.study.stat.controller;

import com.hl.study.common.R;
import com.hl.study.context.CurrentUser;
import com.hl.study.stat.dto.SubjectStat;
import com.hl.study.stat.service.StatService;
import com.hl.study.stat.vo.TodayStatVO;
import com.hl.study.stat.vo.TrendVO;
import com.hl.study.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/stat")
@Tag(name = "统计模块")
public class StatController {

    @Resource
    private StatService statService;

    @GetMapping("/today")
    @Operation(summary = "今日概览")
    public R<TodayStatVO> today(@CurrentUser User user) {
        return R.ok(statService.today(user.getId()));
    }

    @GetMapping("/subject")
    @Operation(summary = "科目正确率分布")
    public R<List<SubjectStat>> subject(@CurrentUser User user) {
        return R.ok(statService.subject(user.getId()));
    }

    @GetMapping("/trend")
    @Operation(summary = "近 N 日趋势")
    public R<TrendVO> trend(
            @CurrentUser User user,
            @RequestParam(defaultValue = "7")
            @Min(1) @Max(30) Integer days) { // 限制 1-30 天，防刷
        return R.ok(statService.trend(user.getId(), days));
    }
}