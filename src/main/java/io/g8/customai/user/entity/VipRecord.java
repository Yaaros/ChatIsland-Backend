package io.g8.customai.user.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * VIP记录类 - 用于记录用户VIP状态变更
 */
@Getter
@Setter
@ToString
public class VipRecord {
    private String uid;
    private Date startTime;     // VIP开始时间
    private Date endTime;       // VIP结束时间（一个月后）
    private boolean active;     // 是否有效
    private String reason;      // 如果被管理员取消，记录原因

    public VipRecord(String uid) {
        this.uid = uid;
        this.startTime = new Date();

        // 设置结束时间为一个月后
        Date end = new Date();
        end.setMonth(end.getMonth() + 1);
        this.endTime = end;

        this.active = true;
        this.reason = null;
    }

    /**
     * 检查VIP是否已过期
     */
    public boolean isExpired() {
        return !active || new Date().after(endTime);
    }

    /**
     * 取消VIP状态
     */
    public void cancelVip(String reason) {
        this.active = false;
        this.reason = reason;
    }
}