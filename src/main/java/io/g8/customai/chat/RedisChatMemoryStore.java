package io.g8.customai.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.g8.customai.chat.config.AiAssistantFactory;
import io.g8.customai.chat.config.ChatType;
import io.g8.customai.chat.config.NonIdAiAssistant;
import io.g8.customai.chat.entity.Session;
import io.g8.customai.chat.mapper.ChatMemoryMapper;
import io.g8.customai.chat.service.SessionTitleInitService;
import io.g8.customai.common.constants.SysEnvs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private final RedisTemplate<String, String> redisTemplate;
    private final ChatMemoryMapper chatMemoryMapper;
    private final ObjectMapper objectMapper;
    private final SessionTitleInitService titleGenerator; // 使用专门的服务
    private static final String REDIS_PREFIX = "chat_memory:";
    private static final String ORIGINAL_PREFIX = "chat_original:";
    private static final int REDIS_EXPIRE_HOURS = 24;
    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);

    @Autowired
    public RedisChatMemoryStore(
            @Qualifier("ai-redis-config") RedisTemplate<String, String> redisTemplate,
            ChatMemoryMapper chatMemoryMapper,
            SessionTitleInitService titleGenerator) {
        this.redisTemplate = redisTemplate;
        this.chatMemoryMapper = chatMemoryMapper;
        this.objectMapper = new ObjectMapper();
        this.titleGenerator = titleGenerator;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = REDIS_PREFIX + memoryId.toString();
        try {
            String redisValue = redisTemplate.opsForValue().get(key);
            if (redisValue != null) {
                List<ChatMessage> messages = deserializeMessages(redisValue);
                log.info("从Redis获取聊天记录, memoryId: {}, 消息数量: {}", memoryId, messages.size());
                return messages;
            }
            Optional<Session> entityOpt = chatMemoryMapper.findById(memoryId.toString());
            if (entityOpt.isPresent()) {
                String messagesJson = entityOpt.get().getMessages();
                List<ChatMessage> messages = deserializeMessages(messagesJson);
                redisTemplate.opsForValue().set(key, messagesJson, Duration.ofHours(REDIS_EXPIRE_HOURS));
                log.info("从数据库获取聊天记录并缓存到Redis, memoryId: {}, 消息数量: {}", memoryId, messages.size());
                return messages;
            }
            log.info("未找到聊天记录, memoryId: {}", memoryId);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取聊天记录失败, memoryId: {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = REDIS_PREFIX + memoryId.toString();
        try {
            String messagesJson = serializeMessages(messages);
            redisTemplate.opsForValue().set(key, messagesJson, Duration.ofHours(REDIS_EXPIRE_HOURS));

            Session entity = chatMemoryMapper.findById(memoryId.toString())
                    .orElse(new Session(memoryId.toString(), messagesJson));
            entity.setMessages(messagesJson);
            entity.setUpdatedTime(LocalDateTime.now());

            // 如果会话名称为空，尝试生成名称
            if (entity.getName() == null || entity.getName().isEmpty()) {
                String name = titleGenerator.generateSessionTitle(messages.getFirst());
                entity.setName(name);
            }
            chatMemoryMapper.save(entity);
            log.info("数据库聊天记录更新成功, memoryId: {}, 消息数量: {}", memoryId, messages.size());
        } catch (Exception e) {
            log.error("更新聊天记录失败, memoryId: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = REDIS_PREFIX + memoryId.toString();
        String originalKey = ORIGINAL_PREFIX + memoryId.toString();
        try {
            redisTemplate.delete(key);
            redisTemplate.delete(originalKey); // 删除原始消息
            chatMemoryMapper.deleteById(memoryId.toString());
            log.info("删除聊天记录成功, memoryId: {}", memoryId);
        } catch (Exception e) {
            log.error("删除聊天记录失败, memoryId: {}", memoryId, e);
        }
    }

    public void createNewSession(String chatId) {
        Session session = new Session(chatId, "[]");
        session.setName(""); // 初始名称为空
        chatMemoryMapper.save(session);
        redisTemplate.opsForValue().set(REDIS_PREFIX + chatId, "[]", Duration.ofHours(REDIS_EXPIRE_HOURS));
        log.info("初始化新会话存储, chatId: {}", chatId);
    }

    private String generateDefaultSessionName(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "会话 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        String cleanedText = message.replaceAll("[^\\p{L}\\p{N}\\s]", "").trim();
        if (cleanedText.length() > 30) {
            cleanedText = cleanedText.substring(0, 30);
        }
        return switch (cleanedText.toLowerCase()) {
            case String s when s.contains("工期") || s.contains("项目") -> "项目工期讨论";
            case String s when s.contains("技术栈") || s.contains("技术") -> "技术栈讨论";
            default -> cleanedText.isEmpty() ? "会话 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : cleanedText;
        };
    }

    private String serializeMessages(List<ChatMessage> messages) throws Exception {
        List<Map<String, Object>> messageData = messages.stream()
                .map(this::chatMessageToMap)
                .collect(Collectors.toList());
        return objectMapper.writeValueAsString(messageData);
    }

    private List<ChatMessage> deserializeMessages(String json) throws Exception {
        List<Map<String, Object>> messageData = objectMapper.readValue(json, new TypeReference<>() {});
        return messageData.stream()
                .map(this::mapToChatMessage)
                .collect(Collectors.toList());
    }

    private Map<String, Object> chatMessageToMap(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", message.type().toString());
        String text = extractMessageText(message);
        map.put("text", text);
        return map;
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage sysMsg) {
            return sysMsg.text();
        }
        return "";
    }

    private ChatMessage mapToChatMessage(Map<String, Object> map) {
        String type = (String) map.get("type");
        String text = (String) map.get("text");
        return switch (type) {
            case "USER" -> UserMessage.from(text);
            case "AI" -> AiMessage.from(text);
            case "SYSTEM" -> SystemMessage.from(text);
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }
}