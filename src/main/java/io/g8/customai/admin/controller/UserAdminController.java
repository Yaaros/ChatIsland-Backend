package io.g8.customai.admin.controller;

import io.g8.customai.admin.util.Utils;
import io.g8.customai.user.service.*;
import io.g8.customai.user.DTO.*;
import io.g8.customai.user.mapper.*;
import io.g8.customai.user.entity.*;
import io.g8.customai.common.security.jwt.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/admin/user")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserQuotaService userQuotaService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ModelInvocationRecordMapper invocationRecordMapper;

    @Autowired
    private VipChangeRecordMapper vipRecordMapper;
    @Value("${vip.auth.key}")
    private String vipKey;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/get-all")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String authHeader,
                                         @RequestParam(defaultValue = "NORMAL") String category,
                                         @RequestParam(required = false, defaultValue = "255") int limit) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            User.Category userCategory;
            try {
                userCategory = User.Category.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "无效的用户类别"));
            }

            List<User> users = userService.findByCategory(userCategory);

            // 安全限制：最多返回 500 个
            int safeLimit = Math.min(Math.max(limit, 1), 65535);

            // 移除敏感信息
            users.stream()
                    .limit(safeLimit)
                    .forEach(user -> user.setPassword(null));

            Map<String, Object> response = new HashMap<>();
            response.put("users", users.stream().limit(safeLimit).toList());
            response.put("category", category);
            response.put("limit", safeLimit);
            response.put("total", users.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取用户列表时发生错误"));
        }
    }


    /**
     * 获取用户详细信息（包括配额使用情况）
     */
    @GetMapping("/get")
    public ResponseEntity<?> getUserDetails(@RequestHeader("Authorization") String authHeader,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String uid) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        if (username == null && uid == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "必须提供 username 或 uid 参数"));
        }

        try {
            User user = null;
            if (username != null) {
                user = userService.findByName(username);
            } else {
                user = userService.findByUid(uid);
            }

            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            UserInfoDTO userInfo = userQuotaService.getUserInfo(user.getUid());

            // 获取VIP历史记录
            List<VipChangeRecord> vipHistory = vipRecordMapper.findHistoryByUid(user.getUid());

            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("userInfo", userInfo);
            response.put("vipHistory", vipHistory);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户详细信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取用户信息时发生错误"));
        }
    }

    /**
     * 创建新用户（管理员权限）
     */
    @PostMapping("/add")
    public ResponseEntity<?> createUser(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody Map<String, String> request) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            String name = request.get("name");
            String password = request.get("password");
            String categoryStr = request.get("category");

            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "用户名和密码不能为空"));
            }

            User.Category category = User.Category.NORMAL;
            if (categoryStr != null && !categoryStr.isEmpty()) {
                try {
                    category = User.Category.valueOf(categoryStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "无效的用户类别"));
                }
            }

            User user = new User();
            user.setName(name);
            user.setPassword(password);
            user.setCategory(category);

            User createdUser = userService.register(user);
            if (createdUser == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "用户名已存在"));
            }

            createdUser.setPassword(null);
            log.info("管理员创建了新用户: {}, 类别: {}", name, category);

            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "创建用户时发生错误"));
        }
    }

    /**
     * 管理员为用户升级VIP（包括续费逻辑）
     */
    @PostMapping("/upgrade-vip")
    public ResponseEntity<?> adminUpgradeVip(@RequestHeader("Authorization") String authHeader,
                                             @RequestBody Map<String, String> request) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            String durationStr = request.get("duration");
            String reason = request.getOrDefault("reason", "管理员操作");
            String username = request.get("username");
            String uid = request.get("uid");
            if(username==null){
                if(uid==null){
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "请指定要移除VIP的用户名"));
                }
                username = userService.findByUid(uid).getName();
            }
            User user = userService.findByName(username);
            if (user == null) {
                return ResponseEntity.badRequest().body("username无法对应到user,请检查user是否存在");
            }
            // 解析天数，默认30天
            int duration = 30;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    duration = Integer.parseInt(durationStr);
                    if (duration <= 0) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "VIP天数必须大于0"));
                    }
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "VIP天数格式无效"));
                }
            }

            boolean success;
            String message;

            if (user.getCategory() == User.Category.VIP) {
                // 如果已经是VIP，执行续费操作
                success = userQuotaService.renewVip(user.getUid(), vipKey, duration);
                message = success ? "VIP续费成功" : "VIP续费失败";
            } else if (user.getCategory() == User.Category.NORMAL) {
                // 如果是普通用户，执行升级操作
                success = userQuotaService.upgradeToVip(user.getUid(), vipKey, duration);
                message = success ? "用户升级为VIP成功" : "用户升级为VIP失败";
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "该用户类型不支持VIP操作"));
            }

            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", message));
            }

            // 获取更新后的用户信息
            UserInfoDTO userInfo = userQuotaService.getUserInfo(user.getUid());

            log.info("管理员为用户 {} 执行VIP操作: {}, 天数: {}, 原因: {}", username, message, duration, reason);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", message,
                    "username", username,
                    "duration", duration,
                    "vipEndTime", userInfo.vipEndTime(),
                    "reason", reason
            ));
        } catch (Exception e) {
            log.error("管理员VIP操作失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "VIP操作过程中发生错误"));
        }
    }

    /**
     * 管理员移除用户VIP权限
     */
    @PostMapping("/remove-vip")
    public ResponseEntity<?> adminRemoveVip(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody Map<String, String> request) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            String token = authHeader.substring(7);
            String adminUid = jwtUtil.getUidFromToken(token);
            String reason = request.getOrDefault("reason", "管理员操作，无具体原因");
            String username = request.get("username");
            String uid = request.get("uid");
            if(username==null){
                if(uid==null){
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "请指定要移除VIP的用户名"));
                }
                username = userService.findByUid(uid).getName();
            }
            boolean success = userQuotaService.removeVipByAdmin(adminUid, username, reason);
            if (success) {
                log.info("管理员移除了用户 {} 的VIP权限，原因: {}", username, reason);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "已成功将用户 " + username + " 降级为普通用户",
                        "reason", reason
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "操作失败，请检查目标用户状态"));
            }
        } catch (Exception e) {
            log.error("管理员移除VIP失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "移除VIP过程中发生错误"));
        }
    }

    /**
     * 更新用户信息
     * input:
     * {
     *     "name":    @Nullable"newName",
     *     "password":@Nullable"newPassword",
     *     "reason": :@Nullable"The reason why do it"
     * }
     * if input.size()==0,return bad request
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateUser(@RequestHeader("Authorization") String authHeader,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String uid,
                                        @RequestBody Map<String, String> request) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        if (username == null && uid == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "必须提供 username 或 uid 参数"));
        }

        try {
            User user = (username != null) ? userService.findByName(username) : userService.findByUid(uid);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            if (request.containsKey("name")) {
                String newName = request.get("name");
                if (newName != null && !newName.isEmpty() && !newName.equals(user.getName())) {
                    if (userService.findByName(newName) != null) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "新用户名已存在"));
                    }
                    user.setName(newName);
                }
            }

            if (request.containsKey("password")) {
                String newPassword = request.get("password");
                if (newPassword != null && !newPassword.isEmpty()) {
                    user.setPassword(newPassword);
                }
            }

            boolean success = userService.updateUser(user);
            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "更新用户失败"));
            }

            user.setPassword(null);
            log.info("管理员更新了用户信息: {}", user.getName());

            return ResponseEntity.ok(Map.of(
                    "message", "用户信息更新成功",
                    "user", user
            ));
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "更新用户时发生错误"));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String authHeader,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String uid) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        if (username == null && uid == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "必须提供 username 或 uid 参数"));
        }

        try {
            User user = (username != null) ? userService.findByName(username) : userService.findByUid(uid);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            if (user.getCategory() == User.Category.ADMIN) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "不能删除管理员账户"));
            }

            boolean success = userService.deleteUser(user.getUid());
            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "删除用户失败"));
            }

            log.info("管理员删除了用户: {}", user.getName());

            return ResponseEntity.ok(Map.of("message", "用户删除成功"));
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "删除用户时发生错误"));
        }
    }


    @GetMapping("/user/invocations")
    public ResponseEntity<?> getUserInvocations(@RequestHeader("Authorization") String authHeader,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String uid,
                                                @RequestParam(defaultValue = "0") int offset,
                                                @RequestParam(defaultValue = "50") int limit) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        if (username == null && uid == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "必须提供 username 或 uid 参数"));
        }

        try {
            User user = (username != null) ? userService.findByName(username) : userService.findByUid(uid);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            List<ModelInvocationRecord> records = invocationRecordMapper.findByUid(user.getUid(), offset, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("username", user.getName());
            response.put("records", records);
            response.put("offset", offset);
            response.put("limit", limit);
            response.put("count", records.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户调用记录失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取调用记录时发生错误"));
        }
    }


    /**
     * 系统统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getSystemStatistics(@RequestHeader("Authorization") String authHeader) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            List<User> normalUsers = userService.findByCategory(User.Category.NORMAL);
            List<User> vipUsers = userService.findByCategory(User.Category.VIP);
            List<User> csUsers = userService.findByCategory(User.Category.CS);
            List<User> adminUsers = userService.findByCategory(User.Category.ADMIN);

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("normalUserCount", normalUsers.size());
            statistics.put("vipUserCount", vipUsers.size());
            statistics.put("csUserCount", csUsers.size());
            statistics.put("adminUserCount", adminUsers.size());
            statistics.put("totalUserCount", normalUsers.size() + vipUsers.size() + csUsers.size() + adminUsers.size());

            // 获取活跃VIP记录数
            Date now = new Date();
            List<VipChangeRecord> activeVipRecords = vipRecordMapper.findExpiredRecords(new Date(now.getTime() + 86400000L)); // 未来一天
            statistics.put("activeVipRecordCount", activeVipRecords.size());

            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取系统统计信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取统计信息时发生错误"));
        }
    }

    /**
     * 手动触发VIP状态检查
     */
    @PostMapping("/vip-status-check")
    public ResponseEntity<?> triggerVipStatusCheck(@RequestHeader("Authorization") String authHeader) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            userQuotaService.checkAndUpdateVipStatus();

            log.info("管理员手动触发了VIP状态检查");

            return ResponseEntity.ok(Map.of(
                    "message", "VIP状态检查已完成",
                    "timestamp", new Date()
            ));
        } catch (Exception e) {
            log.error("VIP状态检查失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "VIP状态检查时发生错误"));
        }
    }

    /**
     * 批量操作 - 根据用户类别获取用户配额使用情况
     */
    @GetMapping("/quota-summary")
    public ResponseEntity<?> getQuotaSummary(@RequestHeader("Authorization") String authHeader,
                                             @RequestParam(defaultValue = "NORMAL") String category) {
        if (!Utils.validateAdminRole(authHeader, jwtUtil, log)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }

        try {
            User.Category userCategory;
            try {
                userCategory = User.Category.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "无效的用户类别"));
            }

            List<User> users = userService.findByCategory(userCategory);
            List<Map<String, Object>> quotaSummary = new ArrayList<>();

            for (User user : users) {
                UserInfoDTO userInfo = userQuotaService.getUserInfo(user.getUid());

                Map<String, Object> userSummary = new HashMap<>();
                userSummary.put("uid", user.getUid());
                userSummary.put("name", user.getName());
                userSummary.put("category", user.getCategory());
                userSummary.put("createTime", user.getCreateTime());
                userSummary.put("dailyLimit", userInfo.dailyLimit());
                userSummary.put("usedToday", userInfo.usedToday());
                userSummary.put("remainingUsage", userInfo.remainingUsage());
                userSummary.put("vipEndTime", userInfo.vipEndTime());

                quotaSummary.add(userSummary);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("category", category);
            response.put("quotaSummary", quotaSummary);
            response.put("total", quotaSummary.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取配额摘要失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取配额摘要时发生错误"));
        }
    }
}