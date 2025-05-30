package io.g8.customai.chat.repository;

import io.g8.customai.chat.entity.ChatMemoryEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// 2. JPA Repository
@Repository
public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, String> {
    // 查询特定用户的所有会话ID
    @Query("SELECT SUBSTRING(c.memoryId, LOCATE('-', c.memoryId) + 1) FROM ChatMemoryEntity c WHERE c.memoryId LIKE CONCAT(:uid, '-%')")
    List<String> findSessionsByUid(@Param("uid") String uid);

    // 根据用户ID查询所有聊天记录
    @Query("SELECT c FROM ChatMemoryEntity c WHERE c.memoryId LIKE CONCAT(:uid, '-%')")
    List<ChatMemoryEntity> findAllByUid(@Param("uid") String uid);

    // 删除用户的所有聊天记录
    @Modifying
    @Query("DELETE FROM ChatMemoryEntity c WHERE c.memoryId LIKE CONCAT(:uid, '-%')")
    void deleteAllByUid(@Param("uid") String uid);
}