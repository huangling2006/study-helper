package com.hl.study.wrong.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hl.study.answer.entity.UserAnswerRecord;
import com.hl.study.answer.mapper.UserAnswerRecordMapper;
import com.hl.study.cache.CacheKeys;
import com.hl.study.cache.RedisCacheService;
import com.hl.study.common.BusinessException;
import com.hl.study.common.ErrorCode;
import com.hl.study.question.entity.Question;
import com.hl.study.question.mapper.QuestionMapper;
import com.hl.study.question.vo.QuestionListVO;
import com.hl.study.stat.service.CheckinBitmap;
import com.hl.study.stat.service.DailyStatCounter;
import com.hl.study.wrong.dto.PracticeAnswerDTO;
import com.hl.study.wrong.dto.PracticeQueryDTO;
import com.hl.study.wrong.dto.PracticeSubmitDTO;
import com.hl.study.wrong.dto.WrongQueryDTO;
import com.hl.study.wrong.mapper.WrongBookMapper;
import com.hl.study.wrong.vo.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WrongBookService {

    @Resource
    private WrongBookMapper wrongBookMapper;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private UserAnswerRecordMapper answerRecordMapper;

    @Resource
    private RedisCacheService redisCacheService;

    // 重练也要计入「今日答题」与「连续打卡」，与 AnswerService 共用同一套 Redis 统计组件
    @Resource
    private DailyStatCounter dailyStatCounter;

    @Resource
    private CheckinBitmap checkinBitmap;

    /**
     * 错题分页列表
     */
    public Page<WrongItemVO> pageWrong(WrongQueryDTO dto, Long userId) {
        Page<WrongItemVO> page = new Page<>(dto.getPage(), dto.getSize());

        // 将前端的字符串转换为 Mapper 需要的枚举/常量格式
        String order = "wrongCount".equals(dto.getOrderBy()) ? "WRONG_COUNT" : "LAST_WRONG_TIME";
        String subject = StringUtils.hasText(dto.getSubject()) ? dto.getSubject().trim() : null;

        return wrongBookMapper.selectWrongPage(page, userId, subject, order);
    }

    /**
     * 手动移除错题
     */
    public void remove(Long wrongId, Long userId) {
        // 核心：SQL 中带上了 user_id，如果删别人的，影响行数必定为 0
        int affected = wrongBookMapper.deleteOwn(wrongId, userId);

        if (affected == 0) {
            // 不存在、或存在但不是你的：对外统一抛出 404，不给资源枚举留口子
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 删除成功，清除 Top100 缓存（防止数据不一致）
        redisCacheService.deleteWithDoubleDelete(CacheKeys.WRONG_HOT_TOP100);
    }

    /**
     * 从错题本抽题（应用层随机）
     */
    public List<QuestionListVO> pickPractice(PracticeQueryDTO dto, Long userId) {
        List<Long> ids = wrongBookMapper.selectWrongQuestionIds(userId, dto.getSubject());
        if (ids.isEmpty()) return List.of();

        Collections.shuffle(ids);
        List<Long> picked = ids.subList(0, Math.min(dto.getSize(), ids.size()));

        List<QuestionListVO> questions = questionMapper.selectListByIds(picked);
        Map<Long, QuestionListVO> byId = new HashMap<>();
        for (QuestionListVO q : questions) {
            byId.put(q.getId(), q);
        }

        List<QuestionListVO> result = new ArrayList<>();
        for (Long id : picked) {
            QuestionListVO vo = byId.get(id);
            if (vo != null) result.add(vo);
        }
        return result;
    }

    /**
     * 提交练习（事务核心）
     *
     * 口径（见 decisions.md D-37/D-38）：
     * 1. 错题重练同样算「今日答题」，写库与 Redis 统计都走 afterCommit，两条链路一套口径；
     * 2. correct = 答对题数（含 SKIPPED），skipped ⊆ correct，因此 total = correct + wrong，
     *    accuracy = correct / total，与单题提交接口的算法一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public PracticeResultVO submitPractice(Long userId, PracticeSubmitDTO dto) {
        int right = 0, wrong = 0, skipped = 0;
        List<PracticeItemVO> details = new ArrayList<>();
        // 与每道题一一对应的判分结果，提交后用于 Redis 埋点
        List<Boolean> correctFlags = new ArrayList<>();

        for (PracticeAnswerDTO a : dto.getAnswers()) {
            Question q = questionMapper.selectById(a.getQuestionId());
            if (q == null) throw new BusinessException(ErrorCode.NOT_FOUND);

            String userAnswer = a.getUserAnswer().trim().toUpperCase();
            boolean isCorrect = q.getAnswer().equalsIgnoreCase(userAnswer);

            // 1. 历史留痕
            UserAnswerRecord record = new UserAnswerRecord();
            record.setUserId(userId);
            record.setQuestionId(q.getId());
            record.setUserAnswer(userAnswer);
            record.setIsCorrect(isCorrect ? 1 : 0);
            answerRecordMapper.insert(record);
            correctFlags.add(isCorrect);

            // 2. 错题状态迁移
            String status;
            if (isCorrect) {
                right++;
                int deleted = wrongBookMapper.deleteByUserAndQuestion(userId, q.getId());
                if (deleted == 0) {
                    // 答对了，但该题已不在错题本（被手动移除或同一批重复提交）
                    status = "SKIPPED";
                    skipped++;
                } else {
                    status = "MASTERED";
                }
            } else {
                wrongBookMapper.upsertWrongBook(userId, q.getId());
                status = "WRONG";
                wrong++;
            }
            details.add(new PracticeItemVO(q.getId(), isCorrect, q.getAnswer(), status, q.getAnalysis()));
        }

        // 3. 只要有状态迁移，就删缓存（挂在事务提交后执行，防脏数据回填）
        if (right > 0 || wrong > 0) {
            redisCacheService.deleteAfterCommit(CacheKeys.WRONG_HOT_TOP100);
        }

        // 4. W6 修复：统计埋点。与 AnswerService 一致，只在事务真正提交后写 Redis，
        //    否则回滚了统计却涨了，对账永远对不上。
        if (!correctFlags.isEmpty()) {
            List<Boolean> flags = List.copyOf(correctFlags);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Boolean ok : flags) {
                        dailyStatCounter.incrQuietly(userId, ok);
                    }
                    checkinBitmap.markQuietly(userId, LocalDate.now());
                }
            });
        }

        int total = dto.getAnswers().size();
        BigDecimal accuracy = total == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(right).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        return new PracticeResultVO(total, right, wrong, skipped, accuracy, details);
    }

    /**
     * 高频错题 Top100（带缓存）
     */
    public HotTop100VO hotTop100() {
        return redisCacheService.getOrLoad(
                CacheKeys.WRONG_HOT_TOP100,
                new TypeReference<HotTop100VO>() {},
                CacheKeys.TTL_HOT,
                () -> {
                    List<HotWrongVO> list = wrongBookMapper.selectHotTop100();
                    return new HotTop100VO(list, LocalDateTime.now());
                }
        );
    }
}