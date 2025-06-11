package io.g8.customai.common.security.utils;

import io.g8.customai.common.security.jwt.JwtUtil;
import org.springframework.http.ResponseEntity;

import java.util.Map;
public class Util {
    public static AuthValidationResult getUid(JwtUtil jwtUtil,
                                              String authHeader,
                                              Map<String,Object> input){
          // 1. 验证Authorization header格式
          if (authHeader == null || !authHeader.startsWith("Bearer ")) {
              return AuthValidationResult.error("Authorization header格式错误");
          }

          String token = authHeader.substring(7);
          String tokenUid = jwtUtil.getUidFromToken(token);

          // 2. 验证请求体中的uid
          Object uidObj = input.get("uid");
          if (uidObj == null) {
              return AuthValidationResult.error("您没有在请求体传入uid");
          }

          String requestUid = uidObj.toString();

          // 3. 检查JWT中的uid和请求uid是否匹配
          if (!tokenUid.equals(requestUid)) {
              String role = jwtUtil.getRoleFromToken(token);
              if (!"ADMIN".equals(role)) {
                  return AuthValidationResult.error("您的JWT和传入UID不匹配,且您的JWT显示您不是管理员,无权操作");
              }
          }

          return AuthValidationResult.success(requestUid);

    }

    public static ResponseEntity<String> validateSelf(JwtUtil jwtUtil,
                                               String authHeader,
                                               String uid) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(403).body("Authorization header格式错误");
        }
        String token = authHeader.substring(7);
        String tokenUid = jwtUtil.getUidFromToken(token);
        if (!tokenUid.equals(uid)) {
            String role = jwtUtil.getRoleFromToken(token);
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(403).body("您只能访问自己的聊天记录");
            }
        }
        return null;
    }
}
