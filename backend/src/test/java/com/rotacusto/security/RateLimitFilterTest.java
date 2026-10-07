package com.rotacusto.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static final String IP_A = "203.0.113.10";
    private static final String IP_B = "203.0.113.20";

    @Test
    void blocksTheCallAfterTheLimitIsReached() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        // POST /api/vehicle-reports — 5 chamadas por 60 min.
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = doPost(filter, "/api/vehicle-reports", IP_A);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse sixth = doPost(filter, "/api/vehicle-reports", IP_A);

        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getContentAsString()).contains("\"status\":429");
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        for (int i = 0; i < 5; i++) {
            assertThat(doPost(filter, "/api/vehicle-reports", IP_A).getStatus()).isEqualTo(200);
        }
        assertThat(doPost(filter, "/api/vehicle-reports", IP_A).getStatus()).isEqualTo(429);

        // IP diferente ainda tem o bucket próprio, cheio.
        assertThat(doPost(filter, "/api/vehicle-reports", IP_B).getStatus()).isEqualTo(200);
    }

    @Test
    void voteWildcardPathMatchesAnyAlertId() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertThat(doPost(filter, "/api/road-alerts/" + i + "/vote", IP_A).getStatus()).isEqualTo(200);
        }

        assertThat(doPost(filter, "/api/road-alerts/999/vote", IP_A).getStatus()).isEqualTo(429);
    }

    @Test
    void routesWithoutARuleAreNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        for (int i = 0; i < 50; i++) {
            assertThat(doPost(filter, "/api/road-alerts/nearby", IP_A).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void tripEstimateIsLimitedSeparatelyFromAlternatives() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        for (int i = 0; i < 30; i++) {
            assertThat(doPost(filter, "/api/trips/estimate", IP_A).getStatus()).isEqualTo(200);
        }
        assertThat(doPost(filter, "/api/trips/estimate", IP_A).getStatus()).isEqualTo(429);

        // Regra própria, bucket próprio — esgotar /estimate não bloqueia /alternatives.
        assertThat(doPost(filter, "/api/trips/estimate/alternatives", IP_A).getStatus()).isEqualTo(200);
    }

    @Test
    void geocodingSuggestIsLimitedOnGet() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        for (int i = 0; i < 120; i++) {
            assertThat(doRequest(filter, "GET", "/api/geocoding/suggest", IP_A, null, null).getStatus())
                    .isEqualTo(200);
        }
        assertThat(doRequest(filter, "GET", "/api/geocoding/suggest", IP_A, null, null).getStatus()).isEqualTo(429);
    }

    @Test
    void usesTheConfiguredHeaderAsClientIpWhenBehindAProxy() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("CF-Connecting-IP");
        String proxyIp = "10.0.0.1";

        // Todo request chega com o mesmo remoteAddr (o proxy) — o bucket tem que ser
        // por cliente real, lido do header.
        for (int i = 0; i < 5; i++) {
            assertThat(doRequest(filter, "POST", "/api/vehicle-reports", proxyIp, "CF-Connecting-IP", IP_A)
                    .getStatus()).isEqualTo(200);
        }
        assertThat(doRequest(filter, "POST", "/api/vehicle-reports", proxyIp, "CF-Connecting-IP", IP_A).getStatus())
                .isEqualTo(429);
        assertThat(doRequest(filter, "POST", "/api/vehicle-reports", proxyIp, "CF-Connecting-IP", IP_B).getStatus())
                .isEqualTo(200);
    }

    @Test
    void takesTheFirstEntryWhenTheConfiguredHeaderIsAList() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("X-Forwarded-For");

        for (int i = 0; i < 5; i++) {
            doRequest(filter, "POST", "/api/vehicle-reports", "10.0.0.1", "X-Forwarded-For", IP_A + ", 172.16.0.9");
        }
        // Mesmo cliente, proxy intermediário diferente: continua no mesmo bucket.
        assertThat(doRequest(filter, "POST", "/api/vehicle-reports", "10.0.0.1", "X-Forwarded-For",
                IP_A + ", 172.16.0.77").getStatus()).isEqualTo(429);
    }

    @Test
    void fallsBackToRemoteAddrWhenTheConfiguredHeaderIsMissing() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("CF-Connecting-IP");

        for (int i = 0; i < 5; i++) {
            assertThat(doPost(filter, "/api/vehicle-reports", IP_A).getStatus()).isEqualTo(200);
        }
        assertThat(doPost(filter, "/api/vehicle-reports", IP_A).getStatus()).isEqualTo(429);
        assertThat(doPost(filter, "/api/vehicle-reports", IP_B).getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresClientSuppliedHeadersWhenNoHeaderIsConfigured() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();

        // Sem header configurado (dev, sem proxy), mandar X-Forwarded-For diferente a
        // cada chamada não dá um bucket novo — vale o remoteAddr.
        for (int i = 0; i < 5; i++) {
            doRequest(filter, "POST", "/api/vehicle-reports", IP_A, "X-Forwarded-For", "198.51.100." + i);
        }
        assertThat(doRequest(filter, "POST", "/api/vehicle-reports", IP_A, "X-Forwarded-For", "198.51.100.99")
                .getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse doPost(RateLimitFilter filter, String uri, String remoteAddr) throws Exception {
        return doRequest(filter, "POST", uri, remoteAddr, null, null);
    }

    private MockHttpServletResponse doRequest(RateLimitFilter filter, String method, String uri, String remoteAddr,
            String headerName, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        if (headerName != null) {
            request.addHeader(headerName, headerValue);
        }
        // Status default do MockHttpServletResponse já é 200 — quando o filtro deixa
        // passar (chain.doFilter chamado, sem controller de verdade nesse teste
        // unitário), o status permanece 200; só o bloqueio de rate limit o sobrescreve.
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
