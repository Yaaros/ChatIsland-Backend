package io.g8.customai.admin.util;

import com.drew.lang.annotations.Nullable;
import io.g8.customai.common.security.jwt.JwtUtil;
import org.slf4j.Logger;

public class Utils {
    public static boolean validateAdminRole(String authHeader,
                                            JwtUtil jwtUtil,
                                            @Nullable Logger log) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return false;
            }

            String token = authHeader.substring(7);
            String role = jwtUtil.getRoleFromToken(token);

            return "ADMIN".equals(role);

        } catch (Exception e) {
            if(log!=null){
                log.error("验证管理员权限失败", e);
            }
            return false;
        }
    }
}
