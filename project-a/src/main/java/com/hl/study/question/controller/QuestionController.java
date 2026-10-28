package com.hl.study.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.common.R;
import com.hl.study.question.dto.QuestionQueryDTO;
import com.hl.study.question.service.QuestionService;
import com.hl.study.question.vo.QuestionDetailVO;
import com.hl.study.question.vo.QuestionListVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/question")
@Validated
public class QuestionController {
    @Resource
    private QuestionService questionService;

    @GetMapping("/list")
    public R<Page<QuestionListVO>> list(@Valid QuestionQueryDTO dto) {
        return R.ok(questionService.listQuestions(dto));
    }

    @GetMapping("/{id}")
    public R<QuestionDetailVO> detail(@PathVariable Long id) {
        return R.ok(questionService.getDetail(id));
    }
}