package io.g8.customai.chat.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    public interface AiAssistant {
        String chat(String msg);
        TokenStream stream(String msg);

    }
    @Bean
    public AiAssistant assistant(ChatLanguageModel clm,
                                 StreamingChatLanguageModel sclm) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        AiAssistant assistant = AiServices.builder(AiAssistant.class)
                                .chatLanguageModel(clm)
                                .streamingChatLanguageModel(sclm)
                                .chatMemory(memory)
                                .build();
        return assistant;
    }
}
