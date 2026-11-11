package com.hl.study.stat.service;

import com.hl.study.stat.dto.AnswerCount;
import com.hl.study.stat.dto.DailyStat;
import com.hl.study.stat.dto.SubjectStat;
import com.hl.study.stat.mapper.StatMapper;
import com.hl.study.stat.vo.SubjectStatVO;
import com.hl.study.stat.vo.TodayStatVO;
import com.hl.study.stat.vo.TrendVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StatService {

    @Resource
    private StatMapper statMapper;
    @Resource
    private DailyStatCounter counter;
    @Resource
    private CheckinBitmap checkin;

    public TodayStatVO today(Long userId) {
        LocalDate day = LocalDate.now();
        AnswerCount c = null;

        try {
            c = counter.read(userId, day);
        } catch (Exception e) {
            log.warn("stat redis unavailable, fallback to db, userId={}, error={}", userId, e.getClass().getSimpleName());
        }

        if (c == null) {
            c = statMapper.countBetween(userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            if (c == null) c = new AnswerCount(0L, 0L);
            counter.backfillQuietly(userId, day, c);
        }

        int streak, lastStreak;
        try {
            streak = checkin.streak(userId, day);
            lastStreak = checkin.streak(userId, day.minusDays(1));
        } catch (Exception e) {
            log.warn("checkin bitmap unavailable, fallback to db, userId={}", userId);
            streak = -1;
            lastStreak = -1;
        }

        return TodayStatVO.builder()
                .date(day.toString())
                .todayTotal(c.getTotal())
                .todayCorrect(c.getCorrect())
                .todayAccuracy(rate(c.getCorrect(), c.getTotal()))
                .todayAnswered(c.getTotal() > 0)
                .streak(streak)
                .lastStreak(lastStreak)
                .build();
    }

    // ================= 【S2 修复】：返回带 accuracy 的 VO =================
    public List<SubjectStatVO> subject(Long userId) {
        LocalDate from30 = LocalDate.now().minusDays(29);
        List<SubjectStat> list = statMapper.selectSubject(userId, from30.atStartOfDay());

        return list.stream().map(s -> {
            SubjectStatVO vo = new SubjectStatVO();
            vo.setSubject(s.getSubject());
            vo.setTotal(s.getTotal());
            vo.setCorrect(s.getCorrect());
            vo.setAccuracy(rate(s.getCorrect(), s.getTotal()));
            return vo;
        }).toList();
    }

    public TrendVO trend(Long userId, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);

        List<DailyStat> rows = statMapper.selectDaily(userId, start.atStartOfDay());
        Map<LocalDate, DailyStat> byDay = rows.stream()
                .collect(Collectors.toMap(DailyStat::getDay, d -> d, (a, b) -> a));

        List<TrendVO.Point> points = new ArrayList<>(days);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DailyStat s = byDay.get(d);
            long total = s == null ? 0 : s.getTotal();
            long correct = s == null ? 0 : s.getCorrect();
            points.add(new TrendVO.Point(d.toString(), total, correct, rate(correct, total)));
        }

        return new TrendVO(days, points);
    }

    private double rate(long correct, long total) {
        return total == 0 ? 0.0 : Math.round(correct * 10000.0 / total) / 10000.0;
    }
}