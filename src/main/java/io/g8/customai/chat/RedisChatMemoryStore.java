package io.g8.customai.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.g8.customai.chat.entity.ChatMemoryEntity;
import io.g8.customai.chat.repository.ChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, String> redisTemplate;

    private final ChatMemoryRepository chatMemoryRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public RedisChatMemoryStore(
            @Qualifier("ai-redis-config") RedisTemplate<String,String> redisTemplate,
            ChatMemoryRepository chatMemoryRepository){
        this.chatMemoryRepository = chatMemoryRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    private static final String REDIS_PREFIX = "chat_memory:";
    private static final int REDIS_EXPIRE_HOURS = 24; // Redis缓存24小时

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = REDIS_PREFIX + memoryId.toString();

        try {
            // 1. 先从Redis获取
            String redisValue = redisTemplate.opsForValue().get(key);
            if (redisValue != null) {
                List<ChatMessage> messages = deserializeMessages(redisValue);
                log.info("从Redis获取聊天记录, memoryId: {}, 消息数量: {}", memoryId, messages.size());
                return messages;
            }

            // 2. Redis没有，从数据库获取
            Optional<ChatMemoryEntity> entityOpt = chatMemoryRepository.findById(memoryId.toString());
            if (entityOpt.isPresent()) {
                String messagesJson = entityOpt.get().getMessages();
                List<ChatMessage> messages = deserializeMessages(messagesJson);

                // 3. 加载到Redis缓存
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

            // 1. 先更新Redis
            redisTemplate.opsForValue().set(key, messagesJson, Duration.ofHours(REDIS_EXPIRE_HOURS));

            // 2. 异步更新数据库
            CompletableFuture.runAsync(() -> {
                try {
                    ChatMemoryEntity entity = chatMemoryRepository.findById(memoryId.toString())
                            .orElse(new ChatMemoryEntity(memoryId.toString(), messagesJson));

                    entity.setMessages(messagesJson);
                    entity.setUpdatedTime(LocalDateTime.now());

                    chatMemoryRepository.save(entity);
                    log.info("数据库聊天记录更新成功, memoryId: {}, 消息数量: {}", memoryId, messages.size());
                } catch (Exception e) {
                    log.error("数据库聊天记录更新失败, memoryId: {}", memoryId, e);
                }
            });

            log.info("Redis聊天记录更新成功, memoryId: {}, 消息数量: {}", memoryId, messages.size());

        } catch (Exception e) {
            log.error("更新聊天记录失败, memoryId: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = REDIS_PREFIX + memoryId.toString();

        try {
            // 1. 删除Redis缓存
            redisTemplate.delete(key);

            // 2. 删除数据库记录
            chatMemoryRepository.deleteById(memoryId.toString());

            log.info("删除聊天记录成功, memoryId: {}", memoryId);
        } catch (Exception e) {
            log.error("删除聊天记录失败, memoryId: {}", memoryId, e);
        }
    }

    // 序列化ChatMessage列表
    private String serializeMessages(List<ChatMessage> messages) throws Exception {
        List<Map<String, Object>> messageData = messages.stream()
                .map(this::chatMessageToMap)
                .collect(Collectors.toList());
        return objectMapper.writeValueAsString(messageData);
    }

    // 反序列化ChatMessage列表
    private List<ChatMessage> deserializeMessages(String json) throws Exception {
        List<Map<String, Object>> messageData = objectMapper.readValue(json,
                new TypeReference<>() {
                });

        return messageData.stream()
                .map(this::mapToChatMessage)
                .collect(Collectors.toList());
    }

    // ChatMessage转Map
    private Map<String, Object> chatMessageToMap(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", message.type().toString());
        map.put("text", message.toString());
        return map;
    }

    // Map转ChatMessage
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

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);
}