package io.g8.customai.common.security.jwt;

import io.g8.customai.user.service.UserService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 一致化处理密钥的方法
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // 如果密钥长度不足，进行填充或哈希处理
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            // 填充剩余部分
            for (int i = keyBytes.length; i < 32; i++) {
                paddedKey[i] = (byte) (i % 256);
            }
            keyBytes = paddedKey;
        }
        // 使用标准的方式创建密钥
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String uid, String username, String role) {
        return Jwts.builder()
                .claim("uid", uid)
                .claim("role", role)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public String getUidFromToken(String token) {
        return (String) getAllClaimsFromToken(token).get("uid");
    }

    public String getRoleFromToken(String token) {
        return (String) getAllClaimsFromToken(token).get("role");
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 修改：严格检查token是否过期
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    // 修改：更严格的token验证逻辑
    public Boolean validateToken(String token) {
        try {
            // 解析token会自动验证签名和过期时间
            Claims claims = getAllClaimsFromToken(token);

            // 检查必要的字段是否存在
            if (claims.getSubject() == null || claims.get("uid") == null) {
                return false;
            }

            // 检查是否过期
            if (isTokenExpired(token)) {
                return false;
            }

            return true;
        } catch (ExpiredJwtException e) {
            // JWT已过期
            System.err.println("JWT token is expired: " + e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            // JWT格式不支持
            System.err.println("JWT token is unsupported: " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            // JWT格式错误
            System.err.println("JWT token is malformed: " + e.getMessage());
            return false;
        } catch (SignatureException e) {
            // 签名验证失败
            System.err.println("JWT signature validation failed: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            // 非法参数
            System.err.println("JWT token compact of handler are invalid: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("JWT token validation failed: " + e.getMessage());
            return false;
        }
    }

    public String getUidFromParamOrJwt(String name,
                                       UserService userService,
                                       String authHeader){
        String uid;
        if(name != null){
            uid = userService.findByName(name).getUid();
        }else{
            uid = this.getUidFromToken(authHeader.replace("Bearer ", ""));
        }
        return uid;
    }
}
