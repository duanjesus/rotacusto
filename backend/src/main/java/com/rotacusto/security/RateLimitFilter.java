package com.rotacusto.security;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
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
 * (relatos anônimos, auth) e pros que gastam cota de serviço externo (rota,
 * autocomplete) — sem Redis, o app roda numa instância só. Roda ANTES
 * do {@link JwtAuthFilter} (registrado antes dele em {@link SecurityConfig}), pra
 * rejeitar barato sem gastar autenticação.
 *
 * <p>Regras hardcoded de propósito (mesmo espírito de
 * {@code RoadAlertService.DURACAO_PADRAO}): poucas rotas fixas, adicionar uma nova
 * já exige mudança de código de qualquer forma.
 *
 * <p>A chave do bucket é {@code regra + IP}. Sem proxy (dev local) o IP vem de
 * {@link HttpServletRequest#getRemoteAddr()}; atrás de um proxy reverso (produção)
 * esse valor é o IP do proxy, igual pra todo mundo, então o IP real é lido do
 * header configurado em {@code rotacusto.rate-limit.client-ip-header}. Esse header
 * tem que ser um que o proxy da hospedagem SOBRESCREVE — um que o cliente consegue
 * mandar por conta própria deixaria qualquer um escolher o próprio bucket.
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
            new Rule("POST", "/api/auth/login", 10, Duration.ofMinutes(15)),
            // Os três abaixo gastam cota de serviço externo gratuito (ORS, Photon),
            // compartilhada por todos os usuários. Folgados de propósito: ida e volta
            // são 2 chamadas, o recálculo de rota na navegação chama de novo, e
            // operadora móvel costuma compartilhar um IP entre vários clientes.
            new Rule("POST", "/api/trips/estimate", 30, Duration.ofMinutes(10)),
            new Rule("POST", "/api/trips/estimate/alternatives", 15, Duration.ofMinutes(10)),
            new Rule("GET", "/api/geocoding/suggest", 120, Duration.ofMinutes(5)));

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final String clientIpHeader;

    public RateLimitFilter() {
        this("");
    }

    @Autowired
    public RateLimitFilter(@Value("${rotacusto.rate-limit.client-ip-header:}") String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Rule rule = findMatchingRule(request);
        if (rule != null) {
            String key = rule.method() + " " + rule.pathPattern() + "|" + resolveClientIp(request);
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rule));
            if (!bucket.tryConsume(1)) {
                writeTooManyRequests(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (StringUtils.hasText(clientIpHeader)) {
            String headerValue = request.getHeader(clientIpHeader);
            if (StringUtils.hasText(headerValue)) {
                // Header em formato de lista (X-Forwarded-For): o cliente é o primeiro item.
                ip = headerValue.split(",")[0].trim();
            }
        }
        // Só pra conferir, num deploy novo, qual header a hospedagem realmente entrega
        // (LOGGING_LEVEL_COM_ROTACUSTO_SECURITY=DEBUG) — desligado no uso normal.
        if (log.isDebugEnabled()) {
            log.debug("rate-limit ip={} remoteAddr={} X-Forwarded-For={} CF-Connecting-IP={} True-Client-IP={}",
                    ip, request.getRemoteAddr(), request.getHeader("X-Forwarded-For"),
                    request.getHeader("CF-Connecting-IP"), request.getHeader("True-Client-IP"));
        }
        return ip;
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
