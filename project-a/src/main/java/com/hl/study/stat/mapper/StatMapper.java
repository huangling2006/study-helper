package com.hl.study.stat.mapper;

import com.hl.study.stat.dto.AnswerCount;
import com.hl.study.stat.dto.DailyStat;
import com.hl.study.stat.dto.SubjectStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StatMapper {

    /**
     * ① 今日：总数 + 正确数（半开区间 [from, to)，索引可用）
     */
    @Select("""
        SELECT COUNT(*) AS total, COALESCE(SUM(is_correct), 0) AS correct
        FROM user_answer_record
        WHERE user_id = #{userId} AND create_time >= #{from} AND create_time < #{to}
    """)
    AnswerCount countBetween(@Param("userId") Long userId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    /**
     * ② 科目分布：近 30 天，JOIN question 拿科目名
     */
    @Select("""
        SELECT q.subject AS subject,
               COUNT(*) AS total,
               SUM(CASE WHEN r.is_correct = 1 THEN 1 ELSE 0 END) AS correct
        FROM user_answer_record r
        JOIN question q ON q.id = r.question_id
        WHERE r.user_id = #{userId} AND r.create_time >= #{from}
        GROUP BY q.subject
        ORDER BY total DESC, q.subject ASC
    """)
    List<SubjectStat> selectSubject(@Param("userId") Long userId,
                                    @Param("from") LocalDateTime from);

    /**
     * ③ 近 N 日趋势：按天聚合，缺的日期由 Service 层补零
     */
    @Select("""
        SELECT DATE(create_time) AS day, COUNT(*) AS total,
               SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct
        FROM user_answer_record
        WHERE user_id = #{userId} AND create_time >= #{from}
        GROUP BY DATE(create_time) ORDER BY day ASC
    """)
    List<DailyStat> selectDaily(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from);
}