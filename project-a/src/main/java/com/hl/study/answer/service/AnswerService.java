package com.hl.study.answer.service;

import com.hl.study.answer.dto.AnswerSubmitDTO;
import com.hl.study.answer.entity.UserAnswerRecord;
import com.hl.study.answer.mapper.UserAnswerRecordMapper;
import com.hl.study.answer.vo.AnswerResultVO;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.question.entity.Question;
import com.hl.study.question.mapper.QuestionMapper;
import com.hl.study.wrong.mapper.WrongBookMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {
    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private UserAnswerRecordMapper answerRecordMapper;
    @Resource
    private WrongBookMapper wrongBookMapper;

    @Transactional(rollbackFor = Exception.class)
    public AnswerResultVO submit(Long userId, AnswerSubmitDTO dto) {
        Question q = questionMapper.selectById(dto.getQuestionId());
        if (q == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        String userAns = dto.getUserAnswer().trim().toUpperCase();
        boolean isCorrect = q.getAnswer().equalsIgnoreCase(userAns);

        UserAnswerRecord record = new UserAnswerRecord();
        record.setUserId(userId);
        record.setQuestionId(dto.getQuestionId());
        record.setUserAnswer(userAns);
        record.setIsCorrect(isCorrect ? 1 : 0);
        answerRecordMapper.insert(record);

        boolean wrongBookAdded = false;
        if (!isCorrect) {
            int affectedRows = wrongBookMapper.upsertWrongBook(userId, dto.getQuestionId());
            wrongBookAdded = (affectedRows == 1);
        }

        return AnswerResultVO.builder()
                .questionId(dto.getQuestionId())
                .isCorrect(isCorrect)
                .correctAnswer(q.getAnswer())
                .analysis(q.getAnalysis())
                .wrongBookAdded(wrongBookAdded)
                .build();
    }
}