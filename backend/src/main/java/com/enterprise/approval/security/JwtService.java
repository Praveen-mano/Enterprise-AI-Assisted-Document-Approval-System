package com.enterprise.approval.security;

import com.enterprise.approval.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final String issuer;

  public JwtService(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.issuer}") String issuer
  ) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
  }

  public String createToken(AppUser user) {
    Instant now = Instant.now();
    return Jwts.builder()
      .issuer(issuer)
      .subject(user.getEmail())
      .claim("role", user.getRoleName())
      .claim("name", user.getDisplayName())
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusSeconds(3600)))
      .signWith(key)
      .compact();
  }

  public Claims parseToken(String token) {
    return Jwts.parser()
      .verifyWith(key)
      .requireIssuer(issuer)
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }
}
