package io.g8.customai.user.controller;

import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.user.DTO.UserInfoDTO;
import io.g8.customai.user.entity.User;
import io.g8.customai.user.service.UserQuotaService;
import io.g8.customai.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user-quota")
public class UserQuotaController {

    private static final Logger logger = LoggerFactory.getLogger(UserQuotaController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserQuotaService userQuotaService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 普通用户升级到VIP
     */
    @PostMapping("/upgrade-vip")
    public ResponseEntity<?> upgradeToVip(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String name,
            @RequestBody Map<String, String> request) {
        String uid = jwtUtil.getUidFromParamOrJwt(name, userService, authHeader);
        String vipKey = request.get("vipKey");
        if (vipKey == null || vipKey.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "VIP密钥不能为空"));
        }

        try {
            boolean success = userQuotaService.upgradeToVip(uid, vipKey);
            if (!success) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "升级失败，请检查VIP密钥或您的账号状态"));
            }

            // 获取更新后的用户信息
            UserInfoDTO userInfo = userQuotaService.getUserInfo(uid);
            User user = userService.findByUid(uid);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功升级到VIP用户");
            response.put("category", user.getCategory());
            response.put("dailyLimit", userInfo.dailyLimit());
            response.put("vipStartTime", userInfo.vipStartTime());
            response.put("vipEndTime", userInfo.vipEndTime());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("VIP升级失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "VIP升级过程中发生错误"));
        }
    }

    /**
     * VIP用户续费
     */
    @PostMapping("/renew-vip")
    public ResponseEntity<?> renewVip(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String name,
            @RequestBody Map<String, String> request) {
        String uid = jwtUtil.getUidFromParamOrJwt(name, userService, authHeader);
        String vipKey = request.get("vipKey");
        if (vipKey == null || vipKey.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "VIP密钥不能为空"));
        }

        try {
            boolean success = userQuotaService.renewVip(uid, vipKey);
            if (!success) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "续费失败，请检查VIP密钥或您的账号状态"));
            }

            // 获取更新后的用户信息
            UserInfoDTO userInfo = userQuotaService.getUserInfo(uid);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "VIP续费成功");
            response.put("newVipEndTime", userInfo.vipEndTime());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("VIP续费失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "VIP续费过程中发生错误"));
        }
    }

    /**
     * 管理员强制降级VIP用户
     */
    @PostMapping("/admin/vip-remove")
    public ResponseEntity<?> removeVipByAdmin(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        String adminUid = jwtUtil.getUidFromParamOrJwt(null, userService, authHeader);
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