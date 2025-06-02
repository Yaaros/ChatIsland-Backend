package io.g8.customai.chat.mapper;

import io.g8.customai.chat.entity.Session;
import io.g8.customai.chat.entity.SessionStatus;
import org.apache.ibatis.annotations.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatMemoryMapper {

    // ========== 原有功能转换 ==========
    @Select("SELECT * FROM chat_memory WHERE memory_id = #{memoryId}")
    Optional<Session> findById(@Param("memoryId") String memoryId);
    /**
     * 保存或更新聊天记录（对应JPA的save）
     */
    @Insert("INSERT INTO chat_memory (memory_id, messages, session_name, created_time, updated_time) " +
            "VALUES (#{memoryId}, #{messages}, #{name}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "messages = VALUES(messages), session_name = VALUES(session_name), updated_time = NOW()")
    int save(Session entity);

    /**
     * 根据memory_id删除聊天记录（对应JPA的deleteById）
     */
    @Delete("DELETE FROM chat_memory WHERE memory_id = #{memoryId}")
    int deleteById(@Param("memoryId") String memoryId);
    /**
     * 查询特定用户的所有会话ID
     */
    @Select("SELECT SUBSTRING(memory_id, LOCATE('-', memory_id) + 1) as session_id " +
            "FROM chat_memory WHERE memory_id LIKE CONCAT(#{uid}, '-%')")
    List<String> findSessionsByUid(@Param("uid") String uid);

    /**
     * 根据用户ID查询所有聊天记录
     */
    @Select("SELECT * FROM chat_memory WHERE memory_id LIKE CONCAT(#{uid}, '-%')")
    List<Session> findAllByUid(@Param("uid") String uid);

    /**
     * 删除用户的所有聊天记录
     */
    @Delete("DELETE FROM chat_memory WHERE memory_id LIKE CONCAT(#{uid}, '-%')")
    int deleteAllByUid(@Param("uid") String uid);

    // ========== 新增功能方法 ==========

    /**
     * 获取用户的默认会话ID（最新创建或最后使用的会话）
     */
    @Select("SELECT SUBSTRING(memory_id, LOCATE('-', memory_id) + 1) as session_id " +
            "FROM chat_memory " +
            "WHERE memory_id LIKE CONCAT(#{uid}, '-%') " +
            "ORDER BY updated_time DESC " +
            "LIMIT 1")
    String getDefaultSessionId(@Param("uid") String uid);

    /**
     * 检查会话是否存在
     */
    @Select("SELECT COUNT(*) FROM chat_memory WHERE memory_id = #{chatId}")
    int sessionExists(@Param("chatId") String chatId);

    /**
     * 获取会话消息数量（通过解析JSON计算）
     */
    @Select("SELECT CASE " +
            "WHEN messages IS NULL OR messages = '' OR messages = '[]' THEN 0 " +
            "ELSE JSON_LENGTH(messages) " +
            "END as message_count " +
            "FROM chat_memory WHERE memory_id = #{chatId}")
    Integer getSessionMessageCount(@Param("chatId") String chatId);
    /**
     * 生成新的会话ID（基于用户现有最大会话ID+1）
     */
    default String generateNewSessionId(String uid) {
        // 获取用户所有会话ID
        List<String> sessionIds = findSessionsByUid(uid);

        // 如果没有会话，则从000001开始
        if (sessionIds.isEmpty()) {
            return "000001";
        }

        // 找出最大的数字会话ID
        int maxId = sessionIds.stream()
                .filter(id -> id.matches("\\d+"))  // 只处理纯数字ID
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);  // 如果没有数字ID，则从0开始

        // 新ID为最大ID+1，格式化为6位数字符串
        return String.format("%06d", maxId + 1);
    }

    /**
     * 创建新会话记录
     */
    @Insert("INSERT INTO chat_memory (memory_id, messages, created_time, updated_time) " +
            "VALUES (#{chatId}, '[]', NOW(), NOW())")
    int createNewSession(@Param("chatId") String chatId);

    /**
     * 获取用户最近的N个会话（按最后更新时间排序）
     */
    @Select("SELECT SUBSTRING(memory_id, LOCATE('-', memory_id) + 1) as session_id, " +
            "session_name, " +
            "updated_time, " +
            "CASE " +
            "WHEN messages IS NULL OR messages = '' OR messages = '[]' THEN 0 " +
            "ELSE JSON_LENGTH(messages) " +
            "END as message_count " +
            "FROM chat_memory " +
            "WHERE memory_id LIKE CONCAT(#{uid}, '-%') " +
            "ORDER BY updated_time DESC " +
            "LIMIT #{limit}")
    @Results({
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "name", column = "session_name"),
            @Result(property = "updatedTime", column = "updated_time"),
            @Result(property = "messageCount", column = "message_count")
    })
    List<SessionStatus> getRecentSessions(@Param("uid") String uid, @Param("limit") int limit);

    /**
     * 获取会话的基本信息（消息数量、最后更新时间等）
     */
    @Select("SELECT memory_id as chat_id, " +
            "CASE " +
            "WHEN messages IS NULL OR messages = '' OR messages = '[]' THEN 0 " +
            "ELSE JSON_LENGTH(messages) " +
            "END as message_count, " +
            "created_time, " +
            "updated_time " +
            "FROM chat_memory WHERE memory_id = #{chatId}")
    @Results({
            @Result(property = "chatId", column = "chat_id"),
            @Result(property = "messageCount", column = "message_count"),
            @Result(property = "createdAt", column = "created_time"),
            @Result(property = "updatedAt", column = "updated_time")
    })
    SessionStatus getSessionStatus(@Param("chatId") String chatId);

    /**
     * 获取会话中最后一条用户消息（用于生成会话标题）
     */
    @Select("SELECT messages FROM chat_memory WHERE memory_id = #{chatId}")
    String getSessionMessagesJson(@Param("chatId") String chatId);

    /**
     * 检查用户是否有任何会话
     */
    @Select("SELECT COUNT(*) FROM chat_memory WHERE memory_id LIKE CONCAT(#{uid}, '-%')")
    int getUserSessionCount(@Param("uid") String uid);

    /**
     * 删除空会话（没有消息的会话）
     */
    @Delete("DELETE FROM chat_memory " +
            "WHERE memory_id LIKE CONCAT(#{uid}, '-%') " +
            "AND (messages IS NULL OR messages = '' OR messages = '[]')")
    int deleteEmptySessionsByUid(@Param("uid") String uid);

    /**
     * 更新会话的最后使用时间
     */
    @Update("UPDATE chat_memory SET updated_time = NOW() WHERE memory_id = #{chatId}")
    int touchSession(@Param("chatId") String chatId);

}