package com.hl.study.answer.controller;

import com.hl.study.answer.dto.AnswerSubmitDTO;
import com.hl.study.answer.service.AnswerService; // 这一行是关键！
import com.hl.study.answer.vo.AnswerResultVO;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.common.R;
import com.hl.study.context.UserContext;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/answer")
public class AnswerController {
    @Resource
    private AnswerService answerService;

    @PostMapping("/submit")
    public R<AnswerResultVO> submit(@RequestBody @Valid AnswerSubmitDTO dto) {
        Long userId = UserContext.get();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return R.ok(answerService.submit(userId, dto));
    }
}