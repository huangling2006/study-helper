package com.hl.study.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hl.study.cache.CacheKeys;
import com.hl.study.cache.RedisCacheService;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.question.dto.QuestionQueryDTO;
import com.hl.study.question.entity.Question;
import com.hl.study.question.mapper.QuestionMapper;
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

    @Resource
    private RedisCacheService redisCacheService;

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
        // 使用 RedisCacheService 包装：查缓存 -> 没命中加锁回源 -> 写空值/抖动回填
        QuestionDetailVO vo = redisCacheService.getOrLoad(
                CacheKeys.QUESTION_DETAIL + id,
                new TypeReference<QuestionDetailVO>() {},
                CacheKeys.TTL_DETAIL,
                () -> {
                    Question q = questionMapper.selectById(id);
                    if (q == null) {
                        return null; // 返回 null，交由 getOrLoad 写入 __NULL__ 哨兵
                    }
                    QuestionDetailVO detailVO = new QuestionDetailVO();
                    BeanUtils.copyProperties(q, detailVO); // 注意：确保 QuestionDetailVO 里没有 answer 和 analysis
                    return detailVO;
                }
        );

        // 如果在缓存里读到的是 __NULL__，上面会返回 null，这里统一抛出 404
        if (vo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        return vo;
    }
}