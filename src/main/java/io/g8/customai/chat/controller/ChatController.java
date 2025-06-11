package io.g8.customai.chat.controller;
import dev.langchain4j.data.message.*;
import io.g8.customai.chat.config.ChatType;
import io.g8.customai.chat.entity.SessionStatus;
import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.common.security.utils.AuthValidationResult;
import io.g8.customai.common.security.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import dev.langchain4j.data.message.ChatMessage;
import io.g8.customai.chat.service.ChatService;

import java.util.stream.Collectors;


import io.g8.customai.user.service.UserQuotaService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ChatService chatService;
    @Autowired
    private UserQuotaService userQuotaService; // Inject UserQuotaService

    @PostMapping(value = "/stream-test", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> memoryStream(
            @RequestBody(required = false) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        String sessionId = extractAndValidate(input, "sessionId", "000001");
        String msg = extractAndValidate(input, "msg", "你是谁");
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        String chatId = uid + "-" + sessionId;
        String modelName = (String) input.getOrDefault("model", "qwen-turbo");

        // Check user's remaining quota
        int remainingUsage = userQuotaService.getRemainingUsage(uid);
        if (remainingUsage <= 0) {
            log.warn("用户 {} 今日配额已用完", uid);
            return Flux.just("error: 今日配额已用完");
        }

        log.info("开始流式对话, chatId: {}, 消息: {}", chatId, msg);
        try {
            // Record the conversation
            boolean recorded = userQuotaService.recordModelInvocation(uid, msg, modelName, 1); // Assume 1 token for simplicity
            if (!recorded) {
                log.error("记录对话失败, chatId: {}", chatId);
                return Flux.just("error: 记录对话失败");
            }
            return chatService.streamChat(chatId, msg, modelName, ChatType.COMMON);
        } catch (Exception e) {
            log.error("启动流式对话失败, chatId: {}", chatId, e);
            return Flux.error(e);
        }
    }

    @PostMapping("/session/create")
    public ResponseEntity<String> createSession(
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        System.out.println("uid="+uid);
        String sessionId = chatService.createNewSession(uid);
        log.info("创建新会话成功, uid: {}, sessionId: {}", uid, sessionId);
        return ResponseEntity.ok(sessionId);
    }

    @PostMapping(value = "/say", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<Flux<String>> say(
            @RequestBody(required = false) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        String sessionId = extractAndValidate(input, "sessionId", "000001");
        String msg = extractAndValidate(input, "msg", "你是谁");
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        String kid = (String) input.getOrDefault("kid", null);
        String modelName = (String) input.getOrDefault("model", "qwen-turbo");
        String chatId = uid + "-" + sessionId;

        // Check user's remaining quota
        int remainingUsage = userQuotaService.getRemainingUsage(uid);
        if (remainingUsage <= 0) {
            log.warn("用户 {} 今日配额已用完", uid);
            return ResponseEntity.ok(Flux.just("error: 今日配额已用完"));
        }

        log.info("开始知识增强流式对话, chatId: {}, 模型: {}, 消息: {}, 知识库: {}", chatId, modelName, msg, kid);
        try {
            // Record the conversation
            boolean recorded = userQuotaService.recordModelInvocation(uid, msg, modelName, 1); // Assume 1 token for simplicity
            if (!recorded) {
                log.error("记录对话失败, chatId: {}", chatId);
                return ResponseEntity.ok(Flux.just("error: 记录对话失败"));
            }
            return ResponseEntity.ok(chatService.streamEnhancedChat(chatId, msg, kid, uid, modelName, ChatType.COMMON));
        } catch (Exception e) {
            log.error("启动增强对话失败, chatId: {}, 模型: {}, 错误: {}", chatId, modelName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Flux.just("error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/session/clear")
    public ResponseEntity<String> clearMemory(
            @RequestBody(required = true) Map<String, Object> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        AuthValidationResult authResult = Util.getUid(jwtUtil, authHeader, input);
        if (!authResult.success()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(authResult.errorMessage());
        }

        Object sessionIdObj = input.get("sessionId");
        if (sessionIdObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("您没有在请求体传入sessionId,系统不知道要删除什么");
        }

        String uid = authResult.uid();
        String sessionId = sessionIdObj.toString();
        String chatId = uid + "-" + sessionId;

        try {
            chatService.deleteMessages(chatId);
            log.info("清理聊天记录成功, chatId: {}", chatId);
            return ResponseEntity.ok("聊天记录清理成功");
        } catch (Exception e) {
            log.error("清理聊天记录失败, chatId: {}", chatId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("清理失败: " + e.getMessage());
        }
    }

    // 管理员可用
    @GetMapping("/session/{uid}/{sessionId}")
    public ResponseEntity<?> getChatMessages(
            @PathVariable String uid,
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        try {
            ResponseEntity<String> FORBIDDEN = Util.validateSelf(jwtUtil,authHeader, uid);
            if (FORBIDDEN != null) return FORBIDDEN;

            String chatId = uid + "-" + sessionId;
            List<ChatMessage> messages = chatService.getMessages(chatId);

            if (messages.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("未找到聊天记录");
            }

            List<Map<String, Object>> messageList = messages.stream()
                    .map(message -> {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("type", message.type().toString());
                        msgMap.put("text", extractMessageText(message));
                        msgMap.put("timestamp", System.currentTimeMillis());
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

    @GetMapping("/sessions/{uid}")
    public ResponseEntity<?> getUserSessions(
            @PathVariable String uid,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        try {
            ResponseEntity<String> FORBIDDEN = Util.validateSelf(jwtUtil,authHeader, uid);
            if (FORBIDDEN != null) return FORBIDDEN;

            List<SessionStatus> sessions = chatService.getUserSessions(uid);
            log.info("获取用户会话列表成功, uid: {}, 会话数量: {}", uid, sessions.size());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("获取用户会话列表失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取会话列表失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<String> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        try {
            String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
            String chatId = uid + "-" + sessionId;
            chatService.deleteMessages(chatId);
            log.info("清除会话历史成功, chatId: {}", chatId);
            return ResponseEntity.ok("会话历史已清除");
        } catch (Exception e) {
            log.error("清除会话历史失败, sessionId: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("清除会话历史失败: " + e.getMessage());
        }
    }

    @PutMapping("/sessions/{sessionId}/rename")
    public ResponseEntity<String> renameSession(
            @RequestBody Map<String, String> input,
            @RequestHeader(value = "Authorization", required = true) String authHeader) {
        String sessionId = input.get("sessionId");
        if(sessionId == null)return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("会话ID不能为空");
        String uid = jwtUtil.getUidFromToken(authHeader.substring(7));
        String newName = input.get("name");
        chatService.renameSession(uid, sessionId, newName);
        log.info("重命名会话成功, chatId: {}-{}, 新名称: {}", uid, sessionId, newName);
        return ResponseEntity.ok("会话已重命名");
    }

    private String extractAndValidate(Map<String, Object> input, String key, String defaultValue) {
        if (input == null) return defaultValue;
        Object value = input.get(key);
        if (value != null && !value.toString().trim().isEmpty()) {
            return value.toString().trim();
        }
        return defaultValue;
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage sysMsg) {
            return sysMsg.text();
        } else if (message instanceof ToolExecutionResultMessage toolMsg) {
            return toolMsg.text();
        } else {
            return message.toString();
        }
    }
}