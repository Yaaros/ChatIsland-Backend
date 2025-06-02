package io.g8.customai.chat.service;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import io.g8.customai.chat.config.NonIdAiAssistant;
import io.g8.customai.common.constants.SysEnvs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionTitleInitService {
    private static final Logger log = LoggerFactory.getLogger(SessionTitleInitService.class);

    private volatile NonIdAiAssistant helper;

    public String generateSessionTitle(ChatMessage firstMessage) {
        System.out.println(firstMessage);
        try {
            NonIdAiAssistant assistant = getOrCreateHelper();
            System.out.println(assistant);
            if (assistant == null) {
                log.warn("AI助手创建失败，返回默认会话标题");
                return "新对话";
            }

            return assistant.chat("以这些内容为主题生成一个简短的用于AI应用的会话标题："
                    + firstMessage
                    + ".将字数控制在10个字以内,标题尽量笼统而不是具体.");
        } catch (Exception e) {
            log.error("生成会话标题失败", e);
            return "新对话";
        }
    }

    private NonIdAiAssistant getOrCreateHelper() {
        if (helper == null) {
            synchronized (this) {
                if (helper == null) {
                    try {
                        helper = createHelper();
                        log.info("AI助手创建成功");
                    } catch (Exception e) {
                        log.error("AI助手创建失败", e);
                        return null;
                    }
                }
            }
        }
        return helper;
    }

    private NonIdAiAssistant createHelper() {
        // 直接根据你的模型创建，不用switch
        ChatLanguageModel clm = QwenChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_KEY"))
                .modelName(SysEnvs.CsModelName)  // 写死你要用的模型
                .build();

        return AiServices.builder(NonIdAiAssistant.class)
                .chatLanguageModel(clm)
                .build();
    }
}