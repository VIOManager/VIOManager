package com.viomanager.common.auth;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 生成与验签工具（HS256）。
 * 载荷五项：userId / role / orgId / realName / pwdUpdatedAt，两个服务共享同一密钥。
 * 时效 2 小时：论坛阶段2 只验签不查库，改密踢人靠短时效自然过期兜底（架构调整方案 §4）。
 */
@Component
public class JwtUtil {

    /** 载荷字段名，token 生成与解析必须用同一组常量 */
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ORG_ID = "orgId";
    public static final String CLAIM_REAL_NAME = "realName";
    public static final String CLAIM_PWD_UPDATED_AT = "pwdUpdatedAt";

    @Value("${vio.jwt.secret}")
    private String secret;

    @Value("${vio.jwt.expire-hours:2}")
    private long expireHours;

    private SecretKey key() {
        // HS256 要求密钥至少 32 字节，短了这里直接抛错（启动期就暴露，好过运行期才发现）
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。pwdUpdatedAt 毫秒值进载荷，改密踢人靠它与库里的最新值比对。
     */
    public String createToken(Long userId, String role, Long orgId, String realName, Long pwdUpdatedAt) {
        Date now = new Date();
        Date expire = Date.from(Instant.now().plus(expireHours, ChronoUnit.HOURS));
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_ORG_ID, orgId)
                .claim(CLAIM_REAL_NAME, realName)
                .claim(CLAIM_PWD_UPDATED_AT, pwdUpdatedAt)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /**
     * 验签并解析载荷。签名不对 / 已过期 / 格式坏，一律抛 1002——
     * 对外不区分失败原因，避免给攻击者透露信息。
     */
    public CurrentUser parseToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        CurrentUser user = new CurrentUser();
        user.setUserId(claims.get(CLAIM_USER_ID, Long.class));
        user.setRole(claims.get(CLAIM_ROLE, String.class));
        user.setOrgId(claims.get(CLAIM_ORG_ID, Long.class));
        user.setRealName(claims.get(CLAIM_REAL_NAME, String.class));
        user.setPwdUpdatedAt(claims.get(CLAIM_PWD_UPDATED_AT, Long.class));
        return user;
    }
}
