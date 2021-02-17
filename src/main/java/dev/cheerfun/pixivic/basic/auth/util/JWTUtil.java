package dev.cheerfun.pixivic.basic.auth.util;

import dev.cheerfun.pixivic.basic.auth.config.AuthProperties;
import dev.cheerfun.pixivic.basic.auth.domain.Authable;
import dev.cheerfun.pixivic.basic.auth.exception.AuthExpirationException;
import dev.cheerfun.pixivic.common.constant.AuthConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/07/18 14:08
 * @description JWT工具类
 */
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class JWTUtil implements Serializable {
    private final AuthProperties authProperties;
    private final JwtParser jwtParser;
    private final SecretKey secretKey;

    public Claims getAllClaimsFromToken(String token) {
        //  JwtParser jwtParser = Jwts.parser().setSigningKey(Keys.hmacShaKeyFor(authProperties.getSecret().getBytes()));
        Claims body = null;
        try {
            body = jwtParser.parseClaimsJws(token).getBody();
        } catch (Exception exception) {
            throw new AuthExpirationException(HttpStatus.UNAUTHORIZED, "token无效");
        }
        return body;
    }

    public String getToken(Authable authable) {
        return generateToken(authable.getIssuer(), authable.getClaims(), null);
    }

    public String getToken(Authable authable, Long expirationTime) {
        return generateToken(authable.getIssuer(), authable.getClaims(), expirationTime);
    }

    public String refreshToken(Claims claims) {
        return generateToken(claims.getIssuer(), claims, null);
    }

    private String generateToken(String issuer, Map<String, Object> claims, Long expirationTime) {
        claims.merge(AuthConstant.REFRESH_COUNT, 0, (value, newValue) -> (Integer) value < 3 ? (Integer) value + 1 : value);
        Integer refreshCount = (Integer) claims.get(AuthConstant.REFRESH_COUNT);
        final Date createdDate = new Date();
        final Date expirationDate = new Date(createdDate.getTime() + (refreshCount + 1) * (expirationTime == null ? authProperties.getExpirationTime() : expirationTime) * 1000);
        return Jwts.builder()
                .setIssuer(issuer)
                .setClaims(claims)
                .setIssuedAt(createdDate)
                .setExpiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    public Map<String, Object> validateToken(String token) {
       /* 成功则返回user 失败抛出未授权异常，但是如果要刷新token，我想也在这里完成，因为如果后面判断token是否过期
        就还需要再解析一次token，解token是比较消耗性能的，因此这里需要一个东西存token
        超时时间可以随着刷新自增长 最大为7天*/
        Claims claims = getAllClaimsFromToken(token);
        long difference = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (difference < 0) {
            //无效 抛token过期异常
            throw new AuthExpirationException(HttpStatus.UNAUTHORIZED, "登录身份信息过期");
        }
        if (difference < authProperties.getRefreshInterval()) {
            //小于一定区间，刷新
            token = refreshToken(claims);
            claims.put("newToken", token);
        }
        return claims;
    }

}
