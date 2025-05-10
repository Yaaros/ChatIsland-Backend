package io.g8.customai.user.service;

/**
 * 用户配额服务接口
 */

import io.g8.customai.user.DTO.UserInfoDTO;

public interface UserQuotaService {

    /**
     * 获取用户信息，包括VIP状态和使用配额
     * @param uid 用户ID
     * @return 用户信息DTO，如果用户不存在返回null
     */
    UserInfoDTO getUserInfo(String uid);

    /**
     * 获取用户剩余使用次数
     * @param uid 用户ID
     * @return 剩余使用次数，如果用户不存在返回0
     */
    int getRemainingUsage(String uid);

    /**
     * 记录模型调用
     * @param uid 用户ID
     * @param content 调用内容
     * @param modelName 模型名称
     * @param tokensUsed 使用的token数量
     * @return 是否记录成功
     */
    boolean recordModelInvocation(String uid, String content, String modelName, int tokensUsed);

    /**
     * 升级用户到VIP
     * @param uid 用户ID
     * @param vipKey VIP密钥
     * @return 是否升级成功
     */
    boolean upgradeToVip(String uid, String vipKey);

    /**
     * 续费VIP
     * @param uid 用户ID
     * @param vipKey VIP密钥
     * @return 是否续费成功
     */
    boolean renewVip(String uid, String vipKey);

    /**
     * 管理员移除VIP权限
     * @param adminUid 管理员用户ID
     * @param targetUsername 目标用户名
     * @param reason 移除原因
     * @return 是否移除成功
     */
    boolean removeVipByAdmin(String adminUid, String targetUsername, String reason);

    /**
     * 检查并更新VIP状态（定时任务）
     */
    void checkAndUpdateVipStatus();
}