package io.g8.customai.user.DTO;
import io.g8.customai.user.entity.User;
import java.util.Date;

/**
 * 用户信息DTO类 - 使用Record类型简化数据传输
 */
public record UserInfoDTO(
        // User基本属性
        String uid,
        String name,
        User.Category category,
        Date createTime,

        // VIP相关信息
        Date vipStartTime,
        Date vipEndTime,
        boolean isVipActive,

        // 配额信息
        int dailyLimit,
        int usedToday,
        int remainingUsage
) {
    /**
     * 从User对象和其他信息构建UserInfoDTO
     */
    public static UserInfoDTO fromUser(User user, VipChangeRecord vipRecord, int dailyLimit, int usedToday, int remainingUsage) {
        Date vipStart = null;
        Date vipEnd = null;
        boolean vipActive = false;

        if (vipRecord != null) {
            vipStart = vipRecord.getStartTime();
            vipEnd = vipRecord.getEndTime();
            vipActive = vipRecord.isActive() && !vipRecord.isExpired();
        }

        return new UserInfoDTO(
                user.getUid(),
                user.getName(),
                user.getCategory(),
                user.getCreateTime(),
                vipStart,
                vipEnd,
                vipActive,
                dailyLimit,
                usedToday,
                remainingUsage
        );
    }
}