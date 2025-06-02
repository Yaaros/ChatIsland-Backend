package io.g8.customai.chat.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import io.g8.customai.chat.RedisChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiAssistantFactory {

    @Autowired
    private RedisChatMemoryStore store;
    @SuppressWarnings("unchecked")
    public Object create(String modelName, ChatType type) {
        ChatLanguageModel clm;
        StreamingChatLanguageModel sclm = switch (modelName) {
            case "qwen-turbo", "qwen-max" -> {
                clm = QwenChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_KEY"))
                        .modelName(modelName)
                        .build();
                yield QwenStreamingChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_KEY"))
                        .modelName(modelName)
                        .build();
            }
            case "deepseek-chat", "deepseek-reasoner" -> {
                clm = OpenAiChatModel.builder()
                        .baseUrl("https://api.deepseek.com")
                        .apiKey(System.getenv("DEEPSEEK_KEY"))
                        .modelName(modelName)
                        .httpClientBuilder(new JdkHttpClientBuilder())
                        .build();
                yield OpenAiStreamingChatModel.builder()
                        .baseUrl("https://api.deepseek.com")
                        .apiKey(System.getenv("DEEPSEEK_KEY"))
                        .modelName(modelName)
                        .httpClientBuilder(new JdkHttpClientBuilder())
                        .build();
            }
            default -> throw new IllegalArgumentException("不支持的模型: " + modelName);
        };

        return type == ChatType.CS
                ? AiServices.builder(NonIdAiAssistant.class)
                .chatLanguageModel(clm)
                .streamingChatLanguageModel(sclm)
                .build()
                : AiServices.builder(AiAssistant.class)
                .chatLanguageModel(clm)
                .streamingChatLanguageModel(sclm)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .id(memoryId)
                        .chatMemoryStore(store)
                        .build())
                .build();
    }
}
