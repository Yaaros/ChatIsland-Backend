package io.g8.customai.chat.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.chat.RedisChatMemoryStore;
import io.g8.customai.chat.config.AiAssistant;
import io.g8.customai.chat.config.AiAssistantFactory;
import io.g8.customai.chat.config.ChatType;
import io.g8.customai.chat.entity.Session;
import io.g8.customai.chat.entity.SessionStatus;
import io.g8.customai.chat.mapper.ChatMemoryMapper;
import io.g8.customai.chat.service.ChatService;
import io.g8.customai.knowledge.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private RedisChatMemoryStore chatMemoryStore;
    @Autowired
    private AiAssistantFactory assistantFactory;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private RagService ragService;
    @Autowired
    private ChatMemoryMapper chatMemoryMapper;
    private AiAssistant assistant;

    @Override
    public Flux<String> streamChat(String chatId, String message,
                                   String modelName, ChatType type) {
        assistant = (AiAssistant) assistantFactory.create(modelName, type);
        TokenStream tokenStream = assistant.stream(chatId, message);
        return Flux.create(sink -> {
            tokenStream.onPartialResponse(sink::next)
                    .onCompleteResponse(response -> {
                        log.info("对话完成, chatId: {}", chatId);
                        sink.complete();
                    })
                    .onError(error -> {
                        log.error("对话出错, chatId: {}", chatId, error);
                        sink.error(error);
                    })
                    .start();
        });
    }

    @Override
    public Flux<String> streamEnhancedChat(String chatId, String message,
                                           String kid, String uid,
                                           String modelName, ChatType type) {
        assistant = (AiAssistant) assistantFactory.create(modelName, type);

        // 仅用于日志记录，不要手动操作
        List<ChatMessage> chatHistory = chatMemoryStore.getMessages(chatId);
        log.info("获取到历史消息数量: {}", chatHistory.size());

        List<String> contexts = new ArrayList<>();
        if (kid != null && !kid.trim().isEmpty()) {
            if (!ragService.knowledgeBaseExists(uid, kid)) {
                return Flux.just("error: 知识库不存在或无权限访问");
            }

            Embedding queryEmbedding = embeddingModel.embed(message).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.7)
                    .build();

            EmbeddingSearchResult<TextSegment> result = ragService.searchForUser(uid, kid, searchRequest);
            contexts = result.matches().stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.toList());
            log.info("知识库搜索完成，找到相关内容数量: {}", contexts.size());
        }

        String augmentedMsg = buildAugmentedMessage(message, contexts);
        System.out.println("Message 内容: " + message);

        // 移除手动内存管理，让LangChain4J自动处理
        // 不要手动调用 chatMemoryStore.appendMessage()

        TokenStream tokenStream = assistant.stream(chatId, augmentedMsg);
        return Flux.create(sink -> {
            tokenStream
                    .onPartialResponse(token -> {
                        sink.next(token);
                    })
                    .onCompleteResponse(response -> {
                        log.info("增强对话完成, chatId: {}", chatId);
                        sink.complete();
                    })
                    .onError(error -> {
                        log.error("增强对话出错, chatId: {}", chatId, error);
                        sink.error(error);
                    })
                    .start();
        });
    }

    @Override
    public List<ChatMessage> getMessages(String chatId) {
        return chatMemoryStore.getMessages(chatId);
    }

    @Override
    public void deleteMessages(String chatId) {
        chatMemoryStore.deleteMessages(chatId);
    }

    @Override
    public List<SessionStatus> getUserSessions(String uid) {
        return chatMemoryMapper.getRecentSessions(uid, 100);
    }

    @Override
    public String buildAugmentedMessage(String userMsg, List<String> contexts) {
        if (contexts.isEmpty()) {
            log.info("未找到相关知识库内容，直接使用原问题");
            return userMsg;
        }
        String context = String.join("\n\n", contexts);
        String augmentedMsg = """
            你是一个智能助手。请根据以下知识库内容来回答用户问题。
            
            【知识库参考内容】：
            %s
            
            【用户问题】：%s
            
            【回答要求】：
            - 优先基于知识库内容回答
            - 如果知识库内容不足以回答问题，可以结合你的知识进行补充
            - 保持回答的准确性和相关性
            - 如果问题与知识库内容无关，可以直接回答用户问题
            """.formatted(context, userMsg);
        log.info("已构建增强消息，知识库内容长度: {}", context.length());
        return augmentedMsg;
    }

    @Override
    public String createNewSession(String uid) {
        String sessionId = chatMemoryMapper.generateNewSessionId(uid);
        String chatId = uid + "-" + sessionId;
        chatMemoryStore.createNewSession(chatId);
        return sessionId;
    }

    @Override
    public void renameSession(String uid, String sessionId, String newName) {
        String chatId = uid + "-" + sessionId;
        Session session = chatMemoryMapper.findById(chatId).orElseThrow(() ->
                new IllegalArgumentException("会话不存在: " + chatId));
        session.setName(newName);
        chatMemoryMapper.save(session);
    }
}