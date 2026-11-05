package com.hl.study.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Service
public class ReportAiService {

    private final ChatClient chatClient;
    private final TemplateReportGenerator template;
    private final ObjectMapper objectMapper;
    private final String userTemplate;
    private final BeanOutputConverter<ReportContent> converter = new BeanOutputConverter<>(ReportContent.class);

    /**
     * 构造器注入：不仅注入了 ChatClient.Builder 和 ObjectMapper，
     * 还读取了 classpath 下的 Prompt 模板文件（避免硬编码），以及降级模板。
     */
    public ReportAiService(ChatClient.Builder builder,
                           TemplateReportGenerator template,
                           ObjectMapper objectMapper,
                           @Value("classpath:prompts/report-user.st") Resource userRes) throws java.io.IOException {
        this.chatClient = builder.build();
        this.template = template;
        this.objectMapper = objectMapper;
        // 正确读取 classpath 下的文件，不带 classpath: 前缀导致的坑
        this.userTemplate = userRes.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 生成报告核心逻辑。
     * 原则：永不向上抛异常，AI 挂了就用 TemplateReportGenerator 兜底。
     */
    public ReportResult generate(ReportStats stats) {
        // 降级 1：无数据，不消耗 token，直接出模板报告
        if (stats.total() == 0) {
            return ReportResult.degraded(template.build(stats, "no_data"), "no_data", "template-v1");
        }

        try {
            ChatResponse resp = chatClient.prompt()
                    .user(u -> u.text(userTemplate)
                            .param("stats", toJson(stats))
                            .param("format", converter.getFormat()))
                    .call()
                    .chatResponse();

            String rawText = resp.getResult().getOutput().getText();
            ReportContent content = converter.convert(rawText);

            if (content == null || content.weakPoints() == null) {
                throw new IllegalStateException("empty structured output");
            }

            return ReportResult.ai(toJson(content), usageTokens(resp), "deepseek-chat");

        } catch (Exception e) {
            // 降级 2：超时 / 401 / 429 / 5xx / 解析失败
            log.warn("ai report degrade, reason={}", e.toString());
            return ReportResult.degraded(template.build(stats, "ai_failed"), brief(e), "template-v1");
        }
    }

    // ================= 以下为辅助方法 =================

    /** 对象序列化，供调用和内部使用 */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("json serialize failed", e);
            return "{}";
        }
    }

    /** 从 ChatResponse 中获取 Token 消耗 */
    private int usageTokens(ChatResponse resp) {
        if (resp.getMetadata() == null || resp.getMetadata().getUsage() == null) {
            return 0;
        }
        Usage usage = resp.getMetadata().getUsage();
        return usage.getTotalTokens() == null ? 0 : usage.getTotalTokens().intValue();
    }

    /** 提取异常摘要，避免 error_msg 长度超限（小于 255 字符） */
    private String brief(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}