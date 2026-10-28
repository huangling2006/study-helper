package com.hl.study.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hl.study.question.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {}