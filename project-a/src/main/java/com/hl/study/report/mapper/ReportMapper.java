package com.hl.study.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hl.study.report.entity.AiReport;
import com.hl.study.report.vo.ReportHistoryVO;
import com.hl.study.stat.dto.AnswerCount;
import com.hl.study.stat.dto.DailyStat;
import com.hl.study.stat.dto.WeakPointRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<AiReport> {

    @Select("""
            SELECT COUNT(*) AS total, COALESCE(SUM(is_correct), 0) AS correct
            FROM user_answer_record
            WHERE user_id = #{userId} AND create_time >= #{from}
            """)
    AnswerCount selectSummary(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT q.subject, q.chapter, COUNT(*) AS total,
                   SUM(CASE WHEN r.is_correct = 0 THEN 1 ELSE 0 END) AS wrong,
                   ROUND(SUM(CASE WHEN r.is_correct = 0 THEN 1 ELSE 0 END) / COUNT(*), 4) AS wrongRate
            FROM user_answer_record r
            JOIN question q ON q.id = r.question_id
            WHERE r.user_id = #{userId} AND r.create_time >= #{from}
            GROUP BY q.subject, q.chapter
            HAVING wrong > 0
            ORDER BY wrong DESC, total DESC, q.chapter ASC
            LIMIT 5
            """)
    List<WeakPointRaw> selectWeakPoints(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS total,
                   SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct
            FROM user_answer_record
            WHERE user_id = #{userId} AND create_time >= #{from}
            GROUP BY DATE(create_time) ORDER BY day ASC
            """)
    List<DailyStat> selectDaily(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT question_id FROM wrong_book
            WHERE user_id = #{userId}
            ORDER BY wrong_count DESC, last_wrong_time DESC
            LIMIT 10
            """)
    List<Long> selectPracticeQuestionIds(@Param("userId") Long userId);

    @Update("""
            UPDATE ai_report
            SET status = #{status}, content = #{content}, token_cost = #{tokens},
                model = #{model}, error_msg = #{errorMsg}, finish_time = NOW()
            WHERE id = #{id} AND status = 0
            """)
    int markSuccessOrDegraded(@Param("id") Long id,
                              @Param("status") Integer status,
                              @Param("content") String content,
                              @Param("tokens") Integer tokens,
                              @Param("model") String model,
                              @Param("errorMsg") String errorMsg);

    // ================= 新增：历史列表 =================

    @Select("""
            SELECT request_id AS requestId,
                   status,
                   token_cost AS tokenCost,
                   model,
                   create_time AS createTime,
                   finish_time AS finishTime,
                   LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(content, '$.overall')), ''), 80) AS overview,
                   CASE WHEN JSON_VALID(content)
                        THEN JSON_LENGTH(COALESCE(JSON_EXTRACT(content, '$.weakPoints'), JSON_ARRAY()))
                        ELSE 0 END AS weakPointCount
            FROM ai_report
            WHERE user_id = #{userId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ReportHistoryVO> selectHistory(@Param("userId") Long userId,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    @Select("SELECT COUNT(*) FROM ai_report WHERE user_id = #{userId}")
    long countHistory(@Param("userId") Long userId);
}