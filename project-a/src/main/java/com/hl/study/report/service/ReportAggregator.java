package com.hl.study.report.service;

import com.hl.study.report.ai.ReportStats;
import com.hl.study.report.mapper.ReportMapper;
import com.hl.study.stat.dto.AnswerCount;
import com.hl.study.stat.dto.DailyStat;
import com.hl.study.stat.dto.WeakPointRaw;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAggregator {

    private final ReportMapper reportMapper;

    public ReportStats collect(Long userId) {
        LocalDateTime from30 = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime from7 = LocalDate.now().minusDays(6).atStartOfDay();

        AnswerCount s = reportMapper.selectSummary(userId, from30);
        List<WeakPointRaw> weak = reportMapper.selectWeakPoints(userId, from30);
        List<DailyStat> daily = reportMapper.selectDaily(userId, from7);
        List<Long> ids = reportMapper.selectPracticeQuestionIds(userId);

        long total = s == null || s.getTotal() == null ? 0 : s.getTotal();
        long correct = s == null || s.getCorrect() == null ? 0 : s.getCorrect();

        return new ReportStats(
                total, correct, rate(correct, total), trend(daily),
                weak.stream().map(w -> new ReportStats.WeakPoint(
                                w.getSubject(), w.getChapter(), w.getTotal(), w.getWrong(), w.getWrongRate()))
                        .limit(5).toList(),
                ids
        );
    }

    /** 近 3 天正确率 - 前 4 天正确率：> 0.05 上升，< -0.05 下降，其余平稳 */
    private String trend(List<DailyStat> daily) {
        if (daily == null || daily.size() < 4) return "平稳";
        int n = daily.size();
        double recent = rate(sum(daily, n - 3, n), total(daily, n - 3, n));
        double earlier = rate(sum(daily, 0, n - 3), total(daily, 0, n - 3));
        if (recent - earlier > 0.05) return "上升";
        if (recent - earlier < -0.05) return "下降";
        return "平稳";
    }

    private long sum(List<DailyStat> list, int from, int to) {
        long s = 0;
        for (int i = from; i < to; i++) s += list.get(i).getCorrect();
        return s;
    }

    private long total(List<DailyStat> list, int from, int to) {
        long s = 0;
        for (int i = from; i < to; i++) s += list.get(i).getTotal();
        return s;
    }

    private double rate(long c, long t) {
        return t == 0 ? 0.0 : Math.round(c * 10000.0 / t) / 10000.0;
    }
}