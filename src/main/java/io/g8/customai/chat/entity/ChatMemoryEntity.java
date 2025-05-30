package io.g8.customai.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "chat_memory")
public class ChatMemoryEntity {
    @Id
    private String memoryId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String messages; // JSON格式存储消息列表

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // 构造器、getter、setter
    public ChatMemoryEntity() {}

    public ChatMemoryEntity(String memoryId, String messages) {
        this.memoryId = memoryId;
        this.messages = messages;
        this.createdTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }

}