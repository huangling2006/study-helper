package com.hl.study.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TemplateReportGenerator {

    private final ObjectMapper objectMapper;

    public String build(ReportStats s, String reason) {
        List<ReportStats.WeakPoint> points = s.weakPoints().stream().limit(5).toList();

        String overall = switch (reason) {
            case "no_data" -> "近 30 天没有答题记录，暂时无法分析。先刷 10 道题再来看报告。";
            default -> String.format("AI 服务繁忙，已生成基础版报告：近 30 天答题 %d 道，正确 %d 道，正确率 %.2f%%，趋势%s。",
                    s.total(), s.correct(), s.accuracy() * 100, s.trend());
        };

        List<String> causes = points.isEmpty()
                ? List.of("错题样本不足，原因分析暂缺")
                : List.of("「" + points.get(0).subject() + "·" + points.get(0).chapter() + "」错误最集中，优先怀疑概念不清",
                "重复错同类的题，说明缺少复盘环节");

        List<String> suggestions = List.of(
                "先把错题本里错得最多的 10 道题重练一遍，答对再移出",
                "对照解析整理每个知识点的边界条件，写进笔记",
                "每天固定 20 分钟做同类题，避免只刷已会的题");

        try {
            return objectMapper.writeValueAsString(new ReportContent(
                    overall,
                    points.stream().map(w -> new ReportContent.WeakPoint(
                            w.subject() + "·" + w.chapter(), (int) w.wrong(), w.wrongRate())).toList(),
                    causes, suggestions, s.practiceQuestionIds(), true
            ));
        } catch (Exception e) {
            throw new RuntimeException("template report serialize failed", e);
        }
    }
}