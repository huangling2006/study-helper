package com.hl.study.wrong.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hl.study.common.R;
import com.hl.study.context.CurrentUser;
import com.hl.study.user.entity.User;
import com.hl.study.wrong.dto.PracticeQueryDTO;
import com.hl.study.wrong.dto.PracticeSubmitDTO;
import com.hl.study.wrong.dto.WrongQueryDTO;
import com.hl.study.wrong.service.WrongBookService;
import com.hl.study.wrong.vo.HotTop100VO;
import com.hl.study.wrong.vo.PracticeResultVO;
import com.hl.study.wrong.vo.WrongItemVO;
import com.hl.study.question.vo.QuestionListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wrong")
@Tag(name = "错题本模块")
public class WrongBookController {

    @Resource
    private WrongBookService wrongBookService;

    @GetMapping("/list")
    @Operation(summary = "错题分页列表")
    public R<Page<WrongItemVO>> list(@Valid WrongQueryDTO dto, @CurrentUser User user) {
        return R.ok(wrongBookService.pageWrong(dto, user.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "手动移除错题")
    public R<Void> remove(@PathVariable Long id, @CurrentUser User user) {
        wrongBookService.remove(id, user.getId());
        return R.ok();
    }

    @GetMapping("/practice")
    @Operation(summary = "从错题本抽题")
    public R<List<QuestionListVO>> practice(@Valid PracticeQueryDTO dto, @CurrentUser User user) {
        return R.ok(wrongBookService.pickPractice(dto, user.getId()));
    }

    @PostMapping("/practice/submit")
    @Operation(summary = "提交练习")
    public R<PracticeResultVO> submitPractice(@Valid @RequestBody PracticeSubmitDTO dto, @CurrentUser User user) {
        return R.ok(wrongBookService.submitPractice(user.getId(), dto));
    }

    @GetMapping("/hot")
    @Operation(summary = "高频错题Top100")
    public R<HotTop100VO> hot() {
        return R.ok(wrongBookService.hotTop100());
    }
}