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
            assertThat(doPost(filter, "/api/trips/estimate", IP_A).getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse doPost(RateLimitFilter filter, String uri, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(remoteAddr);
        // Status default do MockHttpServletResponse já é 200 — quando o filtro deixa
        // passar (chain.doFilter chamado, sem controller de verdade nesse teste
        // unitário), o status permanece 200; só o bloqueio de rate limit o sobrescreve.
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
