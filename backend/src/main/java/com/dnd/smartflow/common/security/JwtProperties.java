package com.dnd.smartflow.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "smartflow.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {
}
