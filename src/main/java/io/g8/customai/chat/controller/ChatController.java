package io.g8.customai.chat.controller;

import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.g8.customai.chat.RedisChatMemoryStore;
import io.g8.customai.chat.config.AiConfig;
import io.g8.customai.chat.repository.ChatMemoryRepository;
import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.common.security.utils.AuthValidationResult;
import io.g8.customai.common.security.utils.Util;
import io.g8.customai.knowledge.deprecated.store.RedisEmbeddingStore;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    @Autowired
    private QwenStreamingChatModel qsc;
    @Autowired
    private JwtUtil jwtUtil;
    @PostMapping(value = "/stream",
                 produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@RequestBody(required = false)Map<String, Object> input) {
        final String todo = (input == null|| input.isEmpty())
                            ?"你是谁"
                            :input.get("msg").toString();
        return Flux.create(sink -> {
            qsc.chat(todo, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    sink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    sink.complete();
                }

                @Override
                public void onError(Throwable throwable) {
                     sink.error(throwable);
                }
            });
        });
    }
    @Autowired
    public AiConfig.AiAssistant assistant;

    @PostMapping(value = "/user-stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> memoryStream(
            @RequestBody(required = false) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {

        // 参数提取和验证
        String sessionId = extractAndValidate(input, "sessionId", "000001");
        String msg = extractAndValidate(input, "msg", "你是谁");
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        // 组合chatId
        final String chatId = uid + "-" + sessionId;
        final String finalMsg = msg;

        log.info("开始流式对话, chatId: {}, 消息: {}", chatId, finalMsg);

        try {
            TokenStream tokenStream = assistant.stream(chatId, finalMsg);

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

        } catch (Exception e) {
            log.error("启动流式对话失败, chatId: {}", chatId, e);
            return Flux.error(e);
        }
    }

    // 工具方法：提取和验证参数
    private String extractAndValidate(Map<String, Object> input, String key, String defaultValue) {
        if (input == null) return defaultValue;

        Object value = input.get(key);
        if (value != null && !value.toString().trim().isEmpty()) {
            return value.toString().trim();
        }
        return defaultValue;
    }

    @Autowired
    private RedisChatMemoryStore chatMemoryStore;
    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    // 清理聊天记录接口
    @DeleteMapping("/memory/clear")
    public ResponseEntity<String> clearMemory(
            @RequestBody(required = true) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {

        // 权限验证
        AuthValidationResult authResult = Util.getUid(jwtUtil, authHeader,input);
        if (!authResult.success()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(authResult.errorMessage());
        }

        // 验证sessionId
        Object sessionIdObj = input.get("sessionId");
        if (sessionIdObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("您没有在请求体传入sessionId,系统不知道要删除什么");
        }

        String uid = authResult.uid();
        String sessionId = sessionIdObj.toString();
        String chatId = uid + "-" + sessionId;

        try {
            // 通过ChatMemoryStore删除
            chatMemoryStore.deleteMessages(chatId);
            log.info("清理聊天记录成功, chatId: {}", chatId);
            return ResponseEntity.ok("聊天记录清理成功");
        } catch (Exception e) {
            log.error("清理聊天记录失败, chatId: {}", chatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("清理失败: " + e.getMessage());
        }
    }

    // 获取聊天记录列表 - 修改为GET请求，使用路径参数
    // 获取聊天记录列表 - 修复JSON序列化问题
    @GetMapping("/memory/{uid}/{sessionId}")
    public ResponseEntity<?> getChatMessages(
            @PathVariable String uid,
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        try {
            ResponseEntity<String> FORBIDDEN = validateAdminRole(authHeader, uid);
            if (FORBIDDEN != null) return FORBIDDEN;

            String chatId = uid + "-" + sessionId;
            // 从ChatMemoryStore获取历史消息
            List<ChatMessage> messages = chatMemoryStore.getMessages(chatId);

            if (messages.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("未找到聊天记录");
            }

            // 转换为前端需要的格式 - 正确提取消息内容
            List<Map<String, Object>> messageList = messages.stream()
                    .map(message -> {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("type", message.type().toString());

                        // 根据消息类型提取文本内容
                        String text = extractMessageText(message);
                        msgMap.put("text", text);

                        return msgMap;
                    })
                    .collect(Collectors.toList());

            log.info("获取聊天记录成功, chatId: {}, 数量: {}", chatId, messages.size());
            return ResponseEntity.ok(messageList);
        } catch (Exception e) {
            log.error("获取聊天记录失败, uid: {}, sessionId: {}", uid, sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取聊天记录失败: " + e.getMessage());
        }
    }

    @Nullable
    private ResponseEntity<String> validateAdminRole(String authHeader, String uid) {
        // 验证Authorization header格式
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Authorization header格式错误");
        }

        String token = authHeader.substring(7);
        String tokenUid = jwtUtil.getUidFromToken(token);

        // 检查权限：用户只能访问自己的数据，管理员可以访问所有数据
        if (!tokenUid.equals(uid)) {
            String role = jwtUtil.getRoleFromToken(token);
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("您只能访问自己的聊天记录");
            }
        }
        return null;
    }

    // 获取用户所有会话列表
    @GetMapping("/memory/sessions/{uid}")
    public ResponseEntity<?> getUserSessions(
            @PathVariable String uid,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {

        try {
            // 权限验证
            ResponseEntity<String> FORBIDDEN = validateAdminRole(authHeader, uid);
            if (FORBIDDEN != null) return FORBIDDEN;

            // 这里需要在Repository中添加方法来查询特定用户的所有会话
            List<String> sessions = chatMemoryRepository.findSessionsByUid(uid);

            log.info("获取用户会话列表成功, uid: {}, 会话数量: {}", uid, sessions.size());
            return ResponseEntity.ok(sessions);

        } catch (Exception e) {
            log.error("获取用户会话列表失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取会话列表失败: " + e.getMessage());
        }
    }
    // 辅助方法：从ChatMessage中提取文本内容
    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) message;
            // 获取用户消息的文本内容
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            // 获取AI消息的文本内容
            return aiMsg.text();
        } else if (message instanceof SystemMessage sysMsg) {
            // 获取系统消息的文本内容
            return sysMsg.text();
        } else if (message instanceof ToolExecutionResultMessage toolMsg) {
            // 获取工具执行结果消息的文本内容
            return toolMsg.text();
        } else {
            // 其他类型的消息，使用toString()作为fallback
            return message.toString();
        }
    }

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private RedisEmbeddingStore embeddingStore;
    @PostMapping(value = "/say", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> say(
            @RequestBody(required = false) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {

        // 参数提取和验证
        String sessionId = extractAndValidate(input, "sessionId", "000001");
        String msg = extractAndValidate(input, "msg", "你是谁");
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        String kid = (String) input.getOrDefault("kid", null); // 知识库ID，默认值

        final String chatId = uid + "-" + sessionId;
        final String finalMsg = msg;

        log.info("开始知识增强流式对话, chatId: {}, 消息: {}, 知识库: {}", chatId, finalMsg, kid);

        try {
            // 1. 嵌入查询向量
            Embedding queryEmbedding = embeddingModel.embed(msg).content();

            // 2. 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.7)
                    .build();

            // 3. 查询知识库
            if(kid!=null){

            }
            EmbeddingSearchResult<TextSegment> result = embeddingStore.searchForUser(uid, kid, searchRequest);

            // 4. 提取上下文
            List<String> contexts = result.matches().stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.toList());

            String context = String.join("\n", contexts);
            String augmentedMsg = context.isEmpty()
                    ? finalMsg
                    : "根据以下知识回答问题：\n" + context + "\n\n问题：" + finalMsg;

            // 5. 调用大模型生成响应流
            TokenStream tokenStream = assistant.stream(chatId, augmentedMsg);

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

        } catch (Exception e) {
            log.error("启动增强对话失败, chatId: {}", chatId, e);
            return Flux.error(e);
        }
    }


}
