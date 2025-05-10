package io.g8.customai.user.DTO;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Date;

/**
 * VIP变动记录实体类
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VipChangeRecord {
    private Long id;               // 主键ID
    private String uid;            // 用户ID

    public enum ChangeType {
        grant,          // 授予VIP
        revoke,         // 管理员撤销
        auto_expired    // 自动过期
    }

    private ChangeType changeType; // 变动类型
    private Date startTime;        // 变动开始时间
    private Date endTime;          // 变动结束时间（过期时间）
    private String reason;         // 变动原因
    private boolean active;        // 是否有效

    /**
     * 创建一个新的VIP授予记录
     */
    public static VipChangeRecord createGrantRecord(String uid, Date endTime) {
        return VipChangeRecord.builder()
                .uid(uid)
                .changeType(ChangeType.grant)
                .startTime(new Date())
                .endTime(endTime)
                .reason("Purchased")
                .active(true)
                .build();
    }

    /**
     * 创建一个撤销记录
     */
    public static VipChangeRecord createRevokeRecord(String uid, String reason) {
        return VipChangeRecord.builder()
                .uid(uid)
                .changeType(ChangeType.revoke)
                .startTime(new Date())
                .reason(reason)
                .active(false)
                .build();
    }

    /**
     * 创建一个自动过期记录
     */
    public static VipChangeRecord createExpiredRecord(String uid) {
        return VipChangeRecord.builder()
                .uid(uid)
                .changeType(ChangeType.auto_expired)
                .startTime(new Date())
                .reason("VIP会员期限已到")
                .active(false)
                .build();
    }

    /**
     * 检查VIP是否已过期
     */
    public boolean isExpired() {
        return !active || (endTime != null && new Date().after(endTime));
    }
}