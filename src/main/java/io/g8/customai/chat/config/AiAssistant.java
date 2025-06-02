package io.g8.customai.chat.config;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AiAssistant {
    String chat(@MemoryId String chatId, @UserMessage String msg);
    TokenStream stream(@MemoryId String chatId, @UserMessage String msg);
}
