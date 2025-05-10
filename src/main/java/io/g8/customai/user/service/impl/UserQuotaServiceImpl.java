package io.g8.customai.user.service.impl;

import io.g8.customai.user.DTO.ModelInvocationRecord;
import io.g8.customai.user.DTO.UserInfoDTO;
import io.g8.customai.user.DTO.VipChangeRecord;
import io.g8.customai.user.entity.User;
import io.g8.customai.user.mapper.ModelInvocationRecordMapper;
import io.g8.customai.user.mapper.VipChangeRecordMapper;
import io.g8.customai.user.service.UserQuotaService;
import io.g8.customai.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Calendar;
import java.util.Date;
@Service
public class UserQuotaServiceImpl implements UserQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UserQuotaServiceImpl.class);

    @Value("${vip.auth.key}")
    private String vipAuthKey;

    @Autowired
    private UserService userService;

    @Autowired
    private VipChangeRecordMapper vipRecordMapper;

    @Autowired
    private ModelInvocationRecordMapper invocationRecordMapper;

    @Override
    public UserInfoDTO getUserInfo(String uid) {
        User user = userService.findByUid(uid);
        if (user == null) {
            return null;
        }

        // 获取VIP记录
        VipChangeRecord vipRecord = vipRecordMapper.findLatestActiveByUid(uid);
        System.out.println(vipRecord);
        // 根据类别获取每日限额
        int dailyLimit = calculateDailyLimit(user.getCategory());

        // 获取今日已使用次数
        int usedToday = invocationRecordMapper.countTodayInvocations(uid);

        // 计算剩余使用次数
        int remainingUsage = (dailyLimit == -1) ? Integer.MAX_VALUE : Math.max(0, dailyLimit - usedToday);

        // 构建并返回DTO
        return UserInfoDTO.fromUser(user, vipRecord, dailyLimit, usedToday, remainingUsage);
    }

    @Override
    public int getRemainingUsage(String uid) {
        User user = userService.findByUid(uid);
        if (user == null) {
            return 0;
        }

        // 如果是ADMIN或CS，不受限制
        if (user.getCategory() == User.Category.ADMIN || user.getCategory() == User.Category.CS) {
            return Integer.MAX_VALUE;
        }

        // 计算每日限额
        int dailyLimit = calculateDailyLimit(user.getCategory());

        // 获取今日已使用次数
        int usedToday = invocationRecordMapper.countTodayInvocations(uid);

        return Math.max(0, dailyLimit - usedToday);
    }

    @Override
    @Transactional
    public boolean recordModelInvocation(String uid, String content, String modelName, int tokensUsed) {
        User user = userService.findByUid(uid);
        if (user == null) {
            return false;
        }

        // 检查是否有足够的使用额度
        int remainingUsage = getRemainingUsage(uid);
        if (remainingUsage <= 0 && user.getCategory() != User.Category.ADMIN && user.getCategory() != User.Category.CS) {
            logger.warn("用户 {} 今日配额已用完，拒绝模型调用", uid);
            return false;
        }

        // 记录调用
        ModelInvocationRecord record = ModelInvocationRecord.createRecord(uid, content, modelName, tokensUsed);
        return invocationRecordMapper.insert(record) > 0;
    }

    @Override
    @Transactional
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
            // 设置VIP结束时间（一个月后）
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, 1);
            Date endTime = calendar.getTime();

            // 创建并保存VIP记录
            VipChangeRecord record = VipChangeRecord.createGrantRecord(uid, endTime);
            vipRecordMapper.insert(record);

            logger.info("用户 {} 成功升级到VIP，有效期至: {}", uid, endTime);
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public boolean renewVip(String uid, String vipKey) {
        // 验证VIP密钥
        if (!vipAuthKey.equals(vipKey)) {
            logger.warn("用户 {} VIP续费请求使用了无效的VIP密钥", uid);
            return false;
        }

        User user = userService.findByUid(uid);
        if (user == null) {
            return false;
        }

        // 只允许VIP用户续费
        if (user.getCategory() != User.Category.VIP) {
            logger.info("用户 {} 不是VIP用户，无法续费", uid);
            return false;
        }

        // 获取当前VIP记录
        VipChangeRecord currentVip = vipRecordMapper.findLatestActiveByUid(uid);

        // 设置新的VIP结束时间（从当前结束时间再延长一个月）
        Calendar calendar = Calendar.getInstance();
        if (currentVip != null && currentVip.getEndTime() != null) {
            calendar.setTime(currentVip.getEndTime());
        }
        calendar.add(Calendar.MONTH, 1);
        Date newEndTime = calendar.getTime();

        // 停用所有现有的VIP记录
        vipRecordMapper.deactivateAllByUid(uid);

        // 创建并保存新的VIP记录
        VipChangeRecord record = VipChangeRecord.createGrantRecord(uid, newEndTime);
        record.setReason("VIP Renewed");
        vipRecordMapper.insert(record);

        logger.info("用户 {} 成功续费VIP，新的有效期至: {}", uid, newEndTime);
        return true;
    }

    @Override
    @Transactional
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
            // 停用所有现有的VIP记录
            vipRecordMapper.deactivateAllByUid(targetUser.getUid());

            // 创建撤销记录
            VipChangeRecord record = VipChangeRecord.createRevokeRecord(targetUser.getUid(), reason);
            vipRecordMapper.insert(record);

            logger.info("管理员 {} 已将用户 {} 降级为普通用户，原因: {}", adminUid, targetUsername, reason);
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public void checkAndUpdateVipStatus() {
        Date now = new Date();
        logger.info("开始检查所有VIP用户状态: {}", now);

        // 获取所有已过期的VIP记录
        var expiredRecords = vipRecordMapper.findExpiredRecords(now);

        for (VipChangeRecord record : expiredRecords) {
            // 标记记录为非活跃
            vipRecordMapper.updateStatus(record.getId(), false, "VIP会员期限已到");

            // 将用户降级为普通用户
            User user = userService.findByUid(record.getUid());
            if (user != null && user.getCategory() == User.Category.VIP) {
                user.setCategory(User.Category.NORMAL);
                userService.updateUser(user);

                // 创建自动过期记录
                VipChangeRecord expiredRecord = VipChangeRecord.createExpiredRecord(user.getUid());
                vipRecordMapper.insert(expiredRecord);

                logger.info("用户 {} 的VIP已过期，已自动降级为普通用户", user.getUid());
            }
        }

        logger.info("VIP用户状态检查完成，共处理 {} 条过期记录", expiredRecords.size());
    }

    /**
     * 根据用户类别计算每日限额
     */
    private int calculateDailyLimit(User.Category category) {
        return switch (category) {
            case NORMAL -> 20;
            case VIP -> 200;
            default -> -1; // 管理员和客服无限制
        };
    }
}