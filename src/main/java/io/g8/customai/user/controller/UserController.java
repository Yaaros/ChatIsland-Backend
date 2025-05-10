package io.g8.customai.user.controller;

import io.g8.customai.common.security.jwt.JwtUtil;
import io.g8.customai.user.entity.User;
import io.g8.customai.user.DTO.UserInfoDTO;
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
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserQuotaService userQuotaService;

    /**
     * 用户注册
     *
     * @param user 用户信息
     *             格式为:
             {
             "name": "testuser",
             "password": "123456",
             "category": "NORMAL"
             }

     *
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        User registeredUser = userService.register(user);

        if (registeredUser == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "用户名已存在");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 清除密码信息后返回
        registeredUser.setPassword(null);
        return new ResponseEntity<>(registeredUser, HttpStatus.CREATED);
    }

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String name = loginRequest.get("name");
        String password = loginRequest.get("password");

        String token = userService.login(name, password);

        if (token == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "用户名或密码错误");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/test-jwt")
    public String testJwt() {

        return "JWT 验证通过，请求成功！";
    }


    /**
     * 获取用户信息
     *
     * @param name:要获取的用户名称
     *            如果没有，则从JWT中读
     * @return 用户信息
     */
    /**
     * 获取用户配额信息
     */
    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader,
                                         @RequestParam String name) {
        String uid = jwtUtil.getUidFromParamOrJwt(name, userService, authHeader);
        UserInfoDTO userInfo;
        try {
            userInfo = userQuotaService.getUserInfo(uid);
            if (userInfo == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("userInfo", userInfo);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取用户信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "获取用户信息时发生错误"));
        }
    }
    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return 更新结果
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        boolean result = userService.updateUser(user);

        if (!result) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "更新失败，用户不存在");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "更新成功");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String authHeader,
                                        @RequestParam String name) {
        String uid = jwtUtil.getUidFromParamOrJwt(name, userService, authHeader);
        boolean result = userService.deleteUser(uid);

        if (!result) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "删除失败，用户不存在");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "删除成功");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}