package io.g8.customai.user.service;

import io.g8.customai.user.entity.UserQuota;

/**
 * 用户配额服务接口
 */
public interface UserQuotaService {

    /**
     * 获取用户当前的配额信息
     */
//    UserQuota getUserQuota(String uid);

    UserQuota getUserQuotaById(String uid);

    UserQuota getUserQuotaByName(String name);

    /**
     * 升级用户到VIP
     * @return 升级是否成功
     */
    boolean upgradeToVip(String uid, String vipKey);

    /**
     * 管理员将VIP用户降级为普通用户
     * @return 降级是否成功
     */
    boolean removeVipByAdmin(String adminUid, String targetUsername, String reason);

    /**
     * 检查并更新所有用户的VIP状态（定时任务调用）
     */
    void checkAndUpdateVipStatus();
}