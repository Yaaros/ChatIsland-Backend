package io.g8.customai.chat.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.TokenStream;
import io.g8.customai.chat.config.AiAssistantFactory;
import io.g8.customai.chat.config.ChatType;
import io.g8.customai.chat.entity.SessionStatus;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {
    Flux<String> streamChat(String chatId, String message,
                            String modelName, ChatType type);
    Flux<String> streamEnhancedChat(String chatId, String message,
                                    String kid, String uid,
                                    String modelName, ChatType type);
    List<ChatMessage> getMessages(String chatId);
    void deleteMessages(String chatId);
    List<SessionStatus> getUserSessions(String uid);
    String buildAugmentedMessage(String userMsg, List<String> contexts);
    String createNewSession(String uid);
    void renameSession(String uid, String sessionId, String newName);
}