package io.g8.customai.user.service.impl;


import io.g8.customai.user.entity.User;
import io.g8.customai.user.entity.UserQuota;
import io.g8.customai.user.entity.VipRecord;
import io.g8.customai.user.service.UserQuotaService;
import io.g8.customai.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户配额服务实现类
 */
@Service
public class UserQuotaServiceImpl implements UserQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UserQuotaServiceImpl.class);

    // 存储用户配额信息（实际应用中应该使用数据库）
    private final Map<String, UserQuota> userQuotaMap = new ConcurrentHashMap<>();

    // 存储VIP记录信息（实际应用中应该使用数据库）
    private final Map<String, VipRecord> vipRecordMap = new ConcurrentHashMap<>();

    @Value("${vip.auth.key:$$$$$$$$$$}")
    private String vipAuthKey;

    @Autowired
    private UserService userService;

    @Override
    public UserQuota getUserQuotaById(String uid) {
        User user = userService.findByUid(uid);
        if (shouldNotRespond(user)) return null;

        // 获取或创建用户配额信息
        UserQuota quota = userQuotaMap.computeIfAbsent(uid,
                k -> new UserQuota(uid, user.getCategory()));

        // 检查并重置每日使用量（如果是新的一天）
        quota.checkAndResetDaily();

        return quota;
    }

    @Override
    public UserQuota getUserQuotaByName(String name) {
        User user = userService.findByName(name);
        if (shouldNotRespond(user)) return null;

        // 获取或创建用户配额信息
//        UserQuota quota = userQuotaMap.computeIfAbsent(uid, k -> new UserQuota(uid, user.getCategory()));
        UserQuota quota = userQuotaMap.computeIfAbsent(name,
                k->new UserQuota(user.getUid(), user.getCategory()));
        // 检查并重置每日使用量（如果是新的一天）
        quota.checkAndResetDaily();

        return quota;
    }

    private static boolean shouldNotRespond(User user) {
        if (user == null) {
            return true;
        }
        // 如果是ADMIN或CS，不返回配额信息
        if (user.getCategory() == User.Category.ADMIN || user.getCategory() == User.Category.CS) {
            return true;
        }
        return false;
    }

    @Override
    public boolean upgradeToVip(String uid, String vipKey) {
        // 验证VIP密钥
        if (!vipAuthKey.equals(vipKey)) {
            logger.warn("用户 {} VIP升级请求使用了无效的VIP密钥", uid);
            return false;
        }

        User user = userService.findByUid(uid);
        if (user == null) {
            return false;
        }

        // 只允许普通用户升级到VIP
        if (user.getCategory() != User.Category.NORMAL) {
            logger.info("用户 {} 不是普通用户，无法升级到VIP", uid);
            return false;
        }

        // 更新用户类别为VIP
        user.setCategory(User.Category.VIP);
        boolean updated = userService.updateUser(user);

        if (updated) {
            // 创建VIP记录
            VipRecord record = new VipRecord(uid);
            vipRecordMap.put(uid, record);

            // 更新用户配额
            UserQuota quota = userQuotaMap.get(uid);
            if (quota != null) {
                quota.updateDailyLimit(User.Category.VIP);
            }

            logger.info("用户 {} 成功升级到VIP，有效期至: {}", uid, record.getEndTime());
            return true;
        }

        return false;
    }

    @Override
    public boolean removeVipByAdmin(String adminUid, String targetUsername, String reason) {
        // 验证管理员权限
        User admin = userService.findByUid(adminUid);
        if (admin == null || admin.getCategory() != User.Category.ADMIN) {
            logger.warn("非管理员 {} 尝试移除VIP权限", adminUid);
            return false;
        }

        // 查找目标用户
        User targetUser = userService.findByName(targetUsername);
        if (targetUser == null) {
            logger.warn("管理员 {} 尝试移除不存在的用户 {}", adminUid, targetUsername);
            return false;
        }

        // 检查目标用户是否为VIP
        if (targetUser.getCategory() != User.Category.VIP) {
            logger.info("用户 {} 不是VIP，无需移除", targetUsername);
            return false;
        }

        // 更新用户类别为普通用户
        targetUser.setCategory(User.Category.NORMAL);
        boolean updated = userService.updateUser(targetUser);

        if (updated) {
            // 更新VIP记录
            VipRecord record = vipRecordMap.get(targetUser.getUid());
            if (record != null) {
                record.cancelVip(reason);
            }

            // 更新用户配额
            UserQuota quota = userQuotaMap.get(targetUser.getUid());
            if (quota != null) {
                quota.updateDailyLimit(User.Category.NORMAL);
            }

            logger.info("管理员 {} 已将用户 {} 降级为普通用户，原因: {}", adminUid, targetUsername, reason);
            return true;
        }

        return false;
    }

    @Override
    public void checkAndUpdateVipStatus() {
        Date now = new Date();
        logger.info("开始检查所有VIP用户状态: {}", now);

        vipRecordMap.forEach((uid, record) -> {
            // 检查VIP是否已过期
            if (record.isActive() && now.after(record.getEndTime())) {
                // 将用户降级为普通用户
                User user = userService.findByUid(uid);
                if (user != null && user.getCategory() == User.Category.VIP) {
                    user.setCategory(User.Category.NORMAL);
                    userService.updateUser(user);

                    // 更新VIP记录
                    record.setActive(false);
                    record.setReason("VIP会员期限已到");

                    // 更新用户配额
                    UserQuota quota = userQuotaMap.get(uid);
                    if (quota != null) {
                        quota.updateDailyLimit(User.Category.NORMAL);
                    }

                    logger.info("用户 {} 的VIP已过期，已自动降级为普通用户", uid);
                }
            }
        });

        logger.info("VIP用户状态检查完成");
    }
}