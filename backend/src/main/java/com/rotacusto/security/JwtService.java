package com.rotacusto.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Gera/valida tokens JWT pra login e histórico de viagens (Fase 6.4b) — o resto da
 * API continua público, só {@code /api/trip-history/**} exige token (ver
 * SecurityConfig).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(
            @Value("${rotacusto.jwt.secret}") String secret,
            @Value("${rotacusto.jwt.expiration-hours}") long expirationHours) {
        // Sem JWT_SECRET configurado, gera um aleatório só pra esse processo —
        // login/histórico continuam funcionando enquanto o processo estiver de pé,
        // mas todo mundo precisa logar de novo a cada restart do back-end. Definir
        // JWT_SECRET (32+ caracteres) é o esperado fora de teste/dev rápido.
        String effectiveSecret = StringUtils.hasText(secret) ? secret : UUID.randomUUID() + "-" + UUID.randomUUID();
        this.key = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String generateToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * @return o e-mail (subject) do token, ou {@code null} se inválido/expirado —
     *         nunca lança exceção, quem chama só trata "autenticado" vs "não".
     */
    public String validateAndGetEmail(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
