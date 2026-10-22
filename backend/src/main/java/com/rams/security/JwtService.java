package com.rams.security;

import com.rams.config.JwtProperties;
import com.rams.domain.UserAccount;
import com.rams.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final UserRepository users;
    private SecretKey key;

    public JwtService(JwtProperties properties, UserRepository users) {
        this.properties = properties;
        this.users = users;
    }

    @PostConstruct
    void init() {
        byte[] secret = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(secret);
    }

    public String createToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(properties.getExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long id = Long.valueOf(claims.getSubject());
        UserAccount user = users.findById(id).orElseThrow(() -> new JwtException("Unknown user"));
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole(), user.isActive());
    }
}
