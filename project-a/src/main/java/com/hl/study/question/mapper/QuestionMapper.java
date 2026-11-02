package com.hl.study.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hl.study.question.entity.Question;
import com.hl.study.question.vo.QuestionListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 根据 ID 列表批量查询题目（只查脱敏字段，不查 answer 和 analysis）
     * 用于错题本抽题
     */
    @Select("""
        <script>
        SELECT q.id, q.subject, q.chapter, q.content,
               q.option_a AS optionA, q.option_b AS optionB,
               q.option_c AS optionC, q.option_d AS optionD, q.difficulty
        FROM question q WHERE q.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        </script>
        """)
    List<QuestionListVO> selectListByIds(@Param("ids") List<Long> ids);
}