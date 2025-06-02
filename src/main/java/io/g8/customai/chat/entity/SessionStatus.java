package io.g8.customai.chat.entity;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SessionStatus {
    private String memoryId;
    private Integer messageCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String name;
    /**
     * 检查是否接近消息限制
     */
    public boolean isNearLimit(int maxMessages) {
        return messageCount != null && messageCount >= (maxMessages * 0.9);
    }
    /**
     * 检查是否超过消息限制
     */
    public boolean isOverLimit(int maxMessages) {
        return messageCount != null && messageCount >= maxMessages;
    }
}
