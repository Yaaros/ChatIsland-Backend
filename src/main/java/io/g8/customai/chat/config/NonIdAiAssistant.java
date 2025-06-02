package io.g8.customai.chat.config;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface NonIdAiAssistant{
    String chat(@UserMessage String msg);
}
