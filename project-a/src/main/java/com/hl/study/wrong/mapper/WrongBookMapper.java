package com.hl.study.wrong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.wrong.entity.WrongBook;
import com.hl.study.wrong.vo.HotWrongVO;
import com.hl.study.wrong.vo.WrongItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WrongBookMapper extends BaseMapper<WrongBook> {

    // 1. W2 已写：答题 upsert
    @Insert("""
        INSERT INTO wrong_book (user_id, question_id, wrong_count, last_wrong_time, add_time)
        VALUES (#{userId}, #{questionId}, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW()
        """)
    int upsertWrongBook(@Param("userId") Long userId, @Param("questionId") Long questionId);

    // 2. 列表分页：使用 <script> 标签支持动态 SQL 和 <choose> 排序白名单
    @Select("""
        <script>
        SELECT w.id AS wrongId, w.question_id AS questionId,
               w.wrong_count AS wrongCount, w.last_wrong_time AS lastWrongTime,
               q.subject, q.chapter, q.content, q.difficulty
        FROM wrong_book w JOIN question q ON q.id = w.question_id
        WHERE w.user_id = #{userId}
        <if test="subject != null and subject != ''"> AND q.subject = #{subject} </if>
        ORDER BY
        <choose>
          <when test="orderBy == 'WRONG_COUNT'"> w.wrong_count DESC, w.last_wrong_time DESC, w.id DESC </when>
          <otherwise> w.last_wrong_time DESC, w.id DESC </otherwise>
        </choose>
        </script>
        """)
    Page<WrongItemVO> selectWrongPage(Page<WrongItemVO> page,
                                      @Param("userId") Long userId,
                                      @Param("subject") String subject,
                                      @Param("orderBy") String orderBy);

    // 3. 越权删除：SQL 必须同时带 id 和 user_id
    @Delete("DELETE FROM wrong_book WHERE id = #{id} AND user_id = #{userId}")
    int deleteOwn(@Param("id") Long id, @Param("userId") Long userId);

    // 4. 抽题：只取 question_id，避免 select * 回表开销
    @Select("""
        SELECT w.question_id FROM wrong_book w JOIN question q ON q.id = w.question_id
        WHERE w.user_id = #{userId} AND (#{subject} IS NULL OR q.subject = #{subject})
        """)
    List<Long> selectWrongQuestionIds(@Param("userId") Long userId, @Param("subject") String subject);

    // 5. 重练答对：删除单条记录
    @Delete("DELETE FROM wrong_book WHERE user_id = #{userId} AND question_id = #{questionId}")
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    // 6. Top100 聚合：按题目全站聚合
    @Select("""
        SELECT w.question_id AS questionId, q.subject, q.chapter, q.content,
               COUNT(*) AS wrongCount, MAX(w.last_wrong_time) AS lastWrongTime
        FROM wrong_book w JOIN question q ON q.id = w.question_id
        GROUP BY w.question_id, q.subject, q.chapter, q.content
        ORDER BY wrongCount DESC, w.question_id ASC LIMIT 100
        """)
    List<HotWrongVO> selectHotTop100();
}