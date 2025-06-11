package io.g8.customai.admin.controller;

import io.g8.customai.admin.util.Utils;
import io.g8.customai.chat.entity.SessionStatus;
import io.g8.customai.chat.mapper.ChatMemoryMapper;
import io.g8.customai.chat.service.ChatService;
import io.g8.customai.common.security.jwt.JwtUtil;
import dev.langchain4j.data.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/chat")
public class ChatAdminController {

    private static final Logger log = LoggerFactory.getLogger(ChatAdminController.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMemoryMapper chatMemoryMapper;

    /**
     * 获取指定用户的所有会话列表
     * GET  admin/chat/sessions/{uid}
     */
    @GetMapping("/sessions/get/{uid}")
    public ResponseEntity<?> getUserSessions(
            @PathVariable String uid,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // 验证管理员权限
            if ( !Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }

            List<SessionStatus> sessions = chatService.getUserSessions(uid);

            log.info("管理员获取用户会话列表成功, uid: {}, 会话数量: {}", uid, sessions.size());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", sessions,
                    "total", sessions.size()
            ));

        } catch (Exception e) {
            log.error("管理员获取用户会话列表失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取会话列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定会话的详细信息和消息记录
     * GET  admin/chat/session/{uid}/{sessionId}
     */
    @GetMapping("/session/get")
    public ResponseEntity<?> getSessionDetail(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {
            // 验证管理员权限
            if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }
            String uid = (String) body.get("uid");
            String sessionId = (String) body.get("sessionId");
            if(uid==null||sessionId==null){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "请传入uid和sessionId"));
            }
            String chatId = uid + "-" + sessionId;
        try{
            // 获取会话状态信息
            SessionStatus sessionStatus = chatMemoryMapper.getSessionStatus(chatId);
            if (sessionStatus == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "会话不存在"));
            }

            // 获取消息记录
            List<ChatMessage> messages = chatService.getMessages(chatId);

            // 转换消息格式
            List<Map<String, Object>> messageList = messages.stream()
                    .map(message -> {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("type", message.type().toString());
                        msgMap.put("text", extractMessageText(message));
                        msgMap.put("timestamp", System.currentTimeMillis());
                        return msgMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("sessionInfo", sessionStatus);
            result.put("messages", messageList);
            result.put("messageCount", messages.size());

            log.info("管理员获取会话详情成功, chatId: {}, 消息数量: {}", chatId, messages.size());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", result
            ));

        } catch (Exception e) {
            log.error("管理员获取会话详情失败:",e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取会话详情失败: " + e.getMessage()));
        }
    }

    /**
     * 删除指定用户的特定会话
     * DELETE  admin/chat/session/{uid}/{sessionId}
     */
    @DeleteMapping("/session/delete")
    public ResponseEntity<?> deleteUserSession(
            @RequestBody(required = true) Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {
            // 验证管理员权限
            if ( !Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }
            String uid = (String) body.get("uid");
            String sessionId = (String) body.get("sessionId");
            if(uid==null||sessionId==null){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "请传入uid和sessionId"));
            }
            String chatId = uid + "-" + sessionId;
            // 检查会话是否存在
            if (chatMemoryMapper.sessionExists(chatId) == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "会话不存在"));
            }
        try{
            // 删除会话消息和记录
            chatService.deleteMessages(chatId);
            chatMemoryMapper.deleteById(chatId);

            log.info("管理员删除用户会话成功, chatId: {}", chatId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "会话删除成功"
            ));

        } catch (Exception e) {
            log.error("管理员删除用户会话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除会话失败: " + e.getMessage()));
        }
    }

    /**
     * 清空指定用户的所有会话记录
     * DELETE /admin/chat/sessions/{uid}/all
     */
    @DeleteMapping("/sessions/delete/{uid}")
    public ResponseEntity<?> deleteAllUserSessions(
            @PathVariable String uid,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // 验证管理员权限
            if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }

            // 获取用户所有会话
            List<SessionStatus> sessions = chatService.getUserSessions(uid);
            int sessionCount = sessions.size();

            // 删除所有会话的消息记录
            for (SessionStatus session : sessions) {
                String chatId = session.getMemoryId();
                chatService.deleteMessages(chatId);
            }

            // 删除数据库中的会话记录
            int deletedCount = chatMemoryMapper.deleteAllByUid(uid);

            log.info("管理员清空用户所有会话成功, uid: {}, 删除会话数: {}", uid, deletedCount);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "用户所有会话已清空",
                    "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            log.error("管理员清空用户所有会话失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "清空会话失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户会话统计信息
     * GET  admin/chat/stats/{uid}
     */
    @GetMapping("/stats/{uid}")
    public ResponseEntity<?> getUserChatStats(
            @PathVariable String uid,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // 验证管理员权限
            if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }
            // 获取用户会话总数
            int totalSessions = chatMemoryMapper.getUserSessionCount(uid);

            // 获取最近的会话
            List<SessionStatus> recentSessions = chatMemoryMapper.getRecentSessions(uid, 10);

            // 计算总消息数
            int totalMessages = recentSessions.stream()
                    .mapToInt(SessionStatus::getMessageCount)
                    .sum();

            // 获取空会话数量
            int emptySessions = (int) recentSessions.stream()
                    .filter(s -> s.getMessageCount() == 0)
                    .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("uid", uid);
            stats.put("totalSessions", totalSessions);
            stats.put("totalMessages", totalMessages);
            stats.put("emptySessions", emptySessions);
            stats.put("activeSessions", totalSessions - emptySessions);
            stats.put("recentSessions", recentSessions);

            log.info("管理员获取用户聊天统计成功, uid: {}, 总会话数: {}", uid, totalSessions);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", stats
            ));

        } catch (Exception e) {
            log.error("管理员获取用户聊天统计失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 清理指定用户的空会话
     * DELETE  admin/chat/sessions/{uid}/empty
     */
    @DeleteMapping("/sessions/clear/{uid}")
    public ResponseEntity<?> deleteEmptyUserSessions(
            @PathVariable String uid,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // 验证管理员权限
            if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }

            // 删除空会话
            int deletedCount = chatMemoryMapper.deleteEmptySessionsByUid(uid);

            log.info("管理员清理用户空会话成功, uid: {}, 删除数量: {}", uid, deletedCount);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "空会话清理完成",
                    "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            log.error("管理员清理用户空会话失败, uid: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "清理空会话失败: " + e.getMessage()));
        }
    }

    /**
     * 强制重命名用户会话
     * PUT  admin/chat/session/{uid}/{sessionId}/rename
     */
    @PutMapping("/session/rename")
    public ResponseEntity<?> renameUserSession(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // 验证管理员权限
            if (!Utils.validateAdminRole(authHeader,jwtUtil,log)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "需要管理员权限"));
            }

            String newName = body.get("newName");
            String sessionId = body.get("sessionId");
            String uid = body.get("uid");
            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "会话名称不能为空"));
            }

            String chatId = uid + "-" + sessionId;

            // 检查会话是否存在
            if (chatMemoryMapper.sessionExists(chatId) == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "会话不存在"));
            }

            // 重命名会话
            chatService.renameSession(uid, sessionId, newName);

            log.info("管理员重命名用户会话成功, chatId: {}, 新名称: {}", chatId, newName);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "会话重命名成功"
            ));

        } catch (Exception e) {
            log.error("管理员重命名用户会话失败, uid: {}, sessionId: {}",
                       body.get("uid"), body.get("sessionId"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "重命名会话失败: " + e.getMessage()));
        }
    }

    /**
     * 提取消息文本内容
     */
    private String extractMessageText(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
            return sysMsg.text();
        } else if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolMsg) {
            return toolMsg.text();
        } else {
            return message.toString();
        }
    }
}