package com.hl.study.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.question.dto.QuestionQueryDTO;
import com.hl.study.question.entity.Question;           // 补上了
import com.hl.study.question.mapper.QuestionMapper;    // 补上了
import com.hl.study.question.vo.QuestionDetailVO;
import com.hl.study.question.vo.QuestionListVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
public class QuestionService {
    @Resource
    private QuestionMapper questionMapper;

    public Page<QuestionListVO> listQuestions(QuestionQueryDTO dto) {
        Page<Question> page = new Page<>(dto.getPage(), dto.getSize());
        var qw = Wrappers.<Question>lambdaQuery();
        qw.eq(StringUtils.hasText(dto.getSubject()), Question::getSubject, dto.getSubject());
        qw.eq(StringUtils.hasText(dto.getChapter()), Question::getChapter, dto.getChapter());
        qw.eq(dto.getDifficulty() != null, Question::getDifficulty, dto.getDifficulty());
        qw.orderByAsc(Question::getId);

        Page<Question> result = questionMapper.selectPage(page, qw);

        Page<QuestionListVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(q -> {
            QuestionListVO vo = new QuestionListVO();
            BeanUtils.copyProperties(q, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    public QuestionDetailVO getDetail(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        QuestionDetailVO vo = new QuestionDetailVO();
        BeanUtils.copyProperties(q, vo);
        return vo;
    }
}