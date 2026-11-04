package com.hl.study.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReportAiService {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    private final BeanOutputConverter<ReportContent> converter = new BeanOutputConverter<>(ReportContent.class);

    public ReportResult generate(String statsJson) {
        try {
            // 直接把 Prompt 硬编码在代码里，避免文件读取问题
            String systemPrompt = "你是一名面向中国高校计算机专业学生的学情分析助手。你只能基于用户提供的统计数据做判断，不得编造题目、科目或数字。\n" +
                    "输出必须是 JSON，字段与给定 schema 完全一致；不要输出 Markdown 代码块、不要输出解释性文字。\n" +
                    "薄弱知识点必须来自 stats.weakPoints，practiceQuestionIds 必须来自 stats.practiceQuestionIds，不允许新增 id。";

            String userPrompt = "以下是一名学生近 30 天的答题统计（JSON）：\n" +
                    statsJson + "\n\n" +
                    "请生成学情诊断：overall（整体表现，含答题数与正确率）、weakPoints（最多 5 条，按错误次数排序）、\n" +
                    "causes（3 条以内原因推断）、suggestions（3--5 条可执行建议）、practiceQuestionIds（优先重练的题目 id）。\n" +
                    converter.getFormat();

            ChatResponse resp = chatClientBuilder.build().prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            String rawText = resp.getResult().getOutput().getText();
            ReportContent content = converter.convert(rawText);

            int tokens = 0;
            Usage usage = resp.getMetadata().getUsage();
            if (usage != null && usage.getTotalTokens() != null) {
                tokens = usage.getTotalTokens().intValue();
            }

            return ReportResult.ai(objectMapper.writeValueAsString(content), tokens, "deepseek-chat");

        } catch (Exception e) {
            log.warn("AI 调用失败，执行降级，原因: {}", e.getMessage());
            // 降级逻辑：用一个最简单的模板拼装
            String fallbackJson = String.format("{\"overall\":\"AI服务繁忙，已生成基础版报告。\",\"weakPoints\":[],\"causes\":[],\"suggestions\":[\"稍后再试\"],\"practiceQuestionIds\":[],\"degraded\":true}");
            return ReportResult.degraded(fallbackJson, e.getMessage(), "template-v1");
        }
    }
}