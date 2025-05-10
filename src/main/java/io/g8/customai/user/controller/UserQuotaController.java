package io.g8.customai.user.controller;

import io.g8.customai.user.entity.User;
import io.g8.customai.user.entity.UserQuota;
import io.g8.customai.user.service.UserQuotaService;
import io.g8.customai.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口控制器
 */
@RestController
@RequestMapping("/api")
public class UserQuotaController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserQuotaService userQuotaService;

    /**
     * 获取用户配额信息
     */
    @PostMapping("/user/quota")
    public ResponseEntity<?> getUserQuota(@RequestBody Map<String, String> requestBody) {
        String uid = requestBody.get("uid");
        String name = requestBody.get("name");

        if (uid == null && name == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "必须提供uid或name参数"));
        }

        UserQuota quota;
        if (uid != null) {
            quota = userQuotaService.getUserQuotaById(uid);
        } else {
            quota = userQuotaService.getUserQuotaByName(name);
        }

        if (quota == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无法获取配额信息或当前用户类型不受配额限制"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dailyLimit", quota.getDailyLimit());
        response.put("usedToday", quota.getUsedToday());
        response.put("remaining", quota.getRemainingUsage());

        return ResponseEntity.ok(response);
    }

    /**
     * 普通用户升级到VIP
     */
    @PostMapping("/user-vip/add")
    public ResponseEntity<?> upgradeToVip(
            @RequestAttribute("uid") String uid,
            @RequestBody Map<String, String> request) {

        String vipKey = request.get("vip-key");
        if (vipKey == null || vipKey.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "VIP密钥不能为空"));
        }

        boolean success = userQuotaService.upgradeToVip(uid, vipKey);

        if (success) {
            User user = userService.findByUid(uid);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功升级到VIP用户");
            response.put("category", user.getCategory());

            // 获取更新后的配额信息
            UserQuota quota = userQuotaService.getUserQuotaById(uid);
            if (quota != null) {
                response.put("newDailyLimit", quota.getDailyLimit());
            }

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "升级失败，请检查VIP密钥或您的账号状态"));
        }
    }

    /**
     * 管理员强制降级VIP用户
     */
    @PostMapping("/admin/vip-remove")
    public ResponseEntity<?> removeVipByAdmin(
            @RequestAttribute("uid") String adminUid,
            @RequestBody Map<String, String> request) {

        String targetUsername = request.get("toRemove");
        String reason = request.get("reason");

        if (targetUsername == null || targetUsername.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "目标用户名不能为空"));
        }

        if (reason == null || reason.isEmpty()) {
            reason = "管理员操作，无具体原因";
        }

        boolean success = userQuotaService.removeVipByAdmin(adminUid, targetUsername, reason);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已成功将用户 " + targetUsername + " 降级为普通用户"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "操作失败，请检查您的权限或目标用户状态"));
        }
    }
}