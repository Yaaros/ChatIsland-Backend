package io.g8.customai.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 获取 SecretKey 实例
    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            String base64Key = Base64.getEncoder().encodeToString(keyBytes);
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Key));
        } catch (Exception e) {
            // 如果解码失败或密钥长度不足，抛出异常
            throw new IllegalArgumentException("Invalid JWT secret key. The key must be Base64 encoded and at least 256 bits (32 bytes) long after decoding.", e);
        }
    }

    /**
     * 生成JWT令牌
     *
     * @param uid      用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return JWT令牌
     */
    public String generateToken(String uid, String username, String role) {
        // 更简单的方式创建密钥
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");

        return Jwts.builder()
                .claim("uid", uid)
                .claim("role", role)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * 从令牌中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token 令牌
     * @return 用户ID
     */
    public String getUidFromToken(String token) {
        return (String) getAllClaimsFromToken(token).get("uid");
    }

    /**
     * 从令牌中获取角色
     *
     * @param token 令牌
     * @return 角色
     */
    public String getRoleFromToken(String token) {
        return (String) getAllClaimsFromToken(token).get("role");
    }

    /**
     * 获取JWT令牌的过期时间
     *
     * @param token 令牌
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从令牌中获取声明
     *
     * @param token          令牌
     * @param claimsResolver 声明解析器
     * @param <T>           声明类型
     * @return 声明
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 从令牌中获取所有声明
     *
     * @param token 令牌
     * @return 所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts
              .parserBuilder()
              .setSigningKey(getSigningKey())
              .build()
              .parseClaimsJws(token)
              .getBody();
//        return Jwts
//              .parser()
//              .setSigningKey(secret)
//              .parseClaimsJws(token)
//              .getBody();
    }

    /**
     * 检查令牌是否过期
     *
     * @param token 令牌
     * @return 是否过期
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 验证令牌
     *
     * @param token 令牌
     * @return 是否有效
     */
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}