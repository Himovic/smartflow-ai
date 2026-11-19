package com.dnd.smartflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("smartflow.security.jwt.secret must be at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(UUID userId, UUID tenantId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** Parses and verifies signature, issuer and expiry. Throws on any invalid token. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(props.issuer()).build().parseSignedClaims(token).getPayload();
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
