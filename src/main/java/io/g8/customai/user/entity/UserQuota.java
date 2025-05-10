package io.g8.customai.user.entity;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.Random;

/**
 * 用户配额信息类
 */
@Getter
@Setter
@ToString
public class UserQuota {
    private String uid;
    private int dailyLimit;        // 每日限额
    private int usedToday;         // 今日已使用
    private Date lastResetDate;    // 最后重置日期

    public UserQuota(String uid, User.Category category) {
        this.uid = uid;
        updateDailyLimit(category);
        this.lastResetDate = new Date();

        // 模拟已使用额度（小于20且一天内不变）
        this.usedToday = generateRandomUsage();
    }

    /**
     * 根据用户类别更新每日限额
     */
    public void updateDailyLimit(User.Category category) {
        switch (category) {
            case NORMAL:
                this.dailyLimit = 20;
                break;
            case VIP:
                this.dailyLimit = 200;
                break;
            default:
                this.dailyLimit = -1; // 管理员和客服无限制
        }
    }

    /**
     * 生成随机用量（小于20且一天内保持不变）
     */
    private int generateRandomUsage() {
        // 使用当天日期作为种子，确保同一天生成相同的随机数
        Date today = new Date();
        long daySeed = today.getTime() / (24 * 60 * 60 * 1000);
        Random random = new Random(daySeed + uid.hashCode());
        return random.nextInt(20);
    }

    /**
     * 检查并重置每日用量（如果是新的一天）
     */
    public void checkAndResetDaily() {
        Date today = new Date();
        // 检查是否为新的一天（日期不同）
        if (today.getDate() != lastResetDate.getDate() ||
                today.getMonth() != lastResetDate.getMonth() ||
                today.getYear() != lastResetDate.getYear()) {

            this.usedToday = generateRandomUsage();
            this.lastResetDate = today;
        }
    }

    /**
     * 获取剩余可用次数
     */
    public int getRemainingUsage() {
        if (dailyLimit == -1) {
            return Integer.MAX_VALUE; // 对于无限制用户
        }
        return Math.max(0, dailyLimit - usedToday);
    }
}
