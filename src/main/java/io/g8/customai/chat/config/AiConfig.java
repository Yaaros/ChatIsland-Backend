package io.g8.customai.chat.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.g8.customai.chat.RedisChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    public interface AiAssistant {
        String chat(@MemoryId String chatId, @UserMessage String msg);
        TokenStream stream(@MemoryId String chatId, @UserMessage String msg);

    }
//    @Bean
//    public AiAssistant assistant(ChatLanguageModel clm,
//                                 StreamingChatLanguageModel sclm) {
////        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
//        AiAssistant assistant = AiServices.builder(AiAssistant.class)
//                                .chatLanguageModel(clm)
//                                .streamingChatLanguageModel(sclm)
//                                .chatMemoryProvider(memoryId->
//                                        MessageWindowChatMemory
//                                        .builder()
//                                        .maxMessages(10)
//                                        .id(memoryId)//id即HashMap的key
//                                        .build()
//                                )
//                                .build();
//        return assistant;
//    }
    @Autowired
    private RedisChatMemoryStore chatMemoryStore;
    @Bean
    public AiAssistant assistant(ChatLanguageModel clm, StreamingChatLanguageModel sclm) {
        AiAssistant assistant = AiServices.builder(AiAssistant.class)
                .chatLanguageModel(clm)
                .streamingChatLanguageModel(sclm)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .maxMessages(10)
                                .id(memoryId)
                                .chatMemoryStore(chatMemoryStore) // 使用自定义存储
                                .build()
                )
                .build();
        return assistant;
    }
}
