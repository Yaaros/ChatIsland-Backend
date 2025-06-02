package io.g8.customai.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "chat_memory")
public class Session {
    @Id
    private String memoryId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String messages; // JSON格式存储消息列表

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Column(name = "session_name")
    private String name;

    // 构造器、getter、setter
    public Session() {}

    public Session(String memoryId, String messages) {
        this.memoryId = memoryId;
        this.messages = messages;
        this.createdTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }
}