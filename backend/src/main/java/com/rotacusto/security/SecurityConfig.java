package com.rotacusto.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Login é opcional pro app inteiro — todo endpoint que já existia antes da Fase
 * 6.4b continua público (cálculo de viagem, catálogo, geocoding, pedágio, relato
 * de veículo). Só {@code /api/trip-history/**} (novo) exige token JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Reaproveita o CORS já configurado em WebConfig — sem isso o Spring
                // Security ignora aquele CorsRegistry e quebra o dev via flutter web.
                .cors(Customizer.withDefaults())
                // API stateless com token no header, sem cookie/sessão — CSRF (que
                // protege contra ataques baseados em cookie de sessão) não se aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/trip-history/**").authenticated()
                        .anyRequest().permitAll())
                // API JSON pura, nunca serve HTML/JS — CSP restritiva é segura. HSTS só
                // tem efeito de verdade sobre HTTPS real (no-op inofensivo em dev local
                // HTTP). X-Frame-Options/X-Content-Type-Options já vêm por padrão do
                // Spring Security, sem precisar de configuração extra.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                // Roda antes da autenticação — rejeita barato, sem gastar validação de JWT.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
