package io.g8.customai.user.schedule;
import io.g8.customai.user.service.UserQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * VIP状态定时检查器
 * 定时检查并更新过期的VIP用户
 */
@Component
public class VipStatusScheduler {

    @Autowired
    private UserQuotaService userQuotaService;

    /**
     * 每天凌晨2点执行一次VIP状态检查
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkVipStatus() {
        userQuotaService.checkAndUpdateVipStatus();
    }
}