package io.g8.customai.user.DTO;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Date;

/**
 * 模型调用记录实体类
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelInvocationRecord {
    private Long id;               // 主键ID
    private String uid;            // 用户ID
    private String content;        // 调用内容
    private String modelName;      // 模型名称
    private Date invocationTime;   // 调用时间
    private int tokensUsed;        // 使用的token数
    private boolean success;       // 调用是否成功

    /**
     * 创建一个新的调用记录
     */
    public static ModelInvocationRecord createRecord(String uid, String content, String modelName, int tokensUsed) {
        return ModelInvocationRecord.builder()
                .uid(uid)
                .content(content)
                .modelName(modelName)
                .invocationTime(new Date())
                .tokensUsed(tokensUsed)
                .success(true)
                .build();
    }
}