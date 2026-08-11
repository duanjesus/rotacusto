package com.rotacusto.security;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.exception.ErrorResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite de taxa em memória, por IP, pros endpoints públicos mais expostos a abuso
 * (relatos anônimos e auth) — sem Redis, o app roda numa instância só. Roda ANTES
 * do {@link JwtAuthFilter} (registrado antes dele em {@link SecurityConfig}), pra
 * rejeitar barato sem gastar autenticação.
 *
 * <p>Regras hardcoded de propósito (mesmo espírito de
 * {@code RoadAlertService.DURACAO_PADRAO}): poucas rotas fixas, adicionar uma nova
 * já exige mudança de código de qualquer forma.
 *
 * <p>A chave do bucket é {@code regra + IP} via {@link HttpServletRequest#getRemoteAddr()}
 * — isso NÃO considera proxy reverso ({@code X-Forwarded-For}), porque o app não
 * está atrás de um hoje. Limitação conhecida, não escondida.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String pathPattern, int capacity, Duration window) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/road-alerts", 5, Duration.ofMinutes(5)),
            new Rule("POST", "/api/road-alerts/*/vote", 10, Duration.ofMinutes(5)),
            new Rule("POST", "/api/traffic-reports", 20, Duration.ofMinutes(5)),
            new Rule("POST", "/api/vehicle-reports", 5, Duration.ofMinutes(60)),
            new Rule("POST", "/api/auth/register", 5, Duration.ofMinutes(60)),
            new Rule("POST", "/api/auth/login", 10, Duration.ofMinutes(15)));

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Rule rule = findMatchingRule(request);
        if (rule != null) {
            String key = rule.method() + " " + rule.pathPattern() + "|" + request.getRemoteAddr();
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rule));
            if (!bucket.tryConsume(1)) {
                writeTooManyRequests(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Rule findMatchingRule(HttpServletRequest request) {
        for (Rule rule : RULES) {
            if (rule.method().equals(request.getMethod()) && pathMatcher.match(rule.pathPattern(), request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }

    private Bucket newBucket(Rule rule) {
        Bandwidth limit = Bandwidth.classic(rule.capacity(), Refill.intervally(rule.capacity(), rule.window()));
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(429, "Muitas requisições — tente novamente em alguns minutos.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
