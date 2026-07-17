package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.entity.TrafficReport;
import com.rotacusto.entity.enums.TrafficSeverity;
import com.rotacusto.repository.TrafficReportRepository;

@ExtendWith(MockitoExtension.class)
class TrafficReportServiceTest {

    @Mock
    private TrafficReportRepository repository;

    private TrafficReportService newService() {
        return new TrafficReportService(repository, 0.3, 15);
    }

    private TrafficReport reportAt(TrafficSeverity severidade, double lat, double lng, Instant expiraEm) {
        TrafficReport r = new TrafficReport();
        r.setSeveridade(severidade);
        r.setLat(lat);
        r.setLng(lng);
        r.setCriadoEm(Instant.now());
        r.setExpiraEm(expiraEm);
        return r;
    }

    @Test
    void reportComputesExpirationFromConfiguredTtl() {
        TrafficReportService service = newService();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrafficReport relato = service.report(TrafficSeverity.INTENSO, -22.9, -43.1);

        long minutos = ChronoUnit.MINUTES.between(relato.getCriadoEm(), relato.getExpiraEm());
        assertEquals(15, minutos, "TTL é único e curto pra qualquer severidade, não varia por tipo");
    }

    @Test
    void findNearPointOnlyReturnsReportsWithinRadius() {
        TrafficReportService service = newService();
        Instant futuro = Instant.now().plusSeconds(600);
        TrafficReport perto = reportAt(TrafficSeverity.MEDIO, -22.90, -43.10, futuro);
        TrafficReport longe = reportAt(TrafficSeverity.MEDIO, -23.50, -46.60, futuro); // São Paulo, bem longe
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(perto, longe));

        List<TrafficReport> encontrados = service.findNearPoint(new Coordinates(-22.90, -43.10), 1.0);

        assertEquals(1, encontrados.size());
        assertEquals(perto, encontrados.get(0));
    }

    @Test
    void findNearRouteMatchesAnyPointAlongTheRoute() {
        TrafficReportService service = newService();
        Instant futuro = Instant.now().plusSeconds(600);
        TrafficReport naRota = reportAt(TrafficSeverity.INTENSO, -22.85, -42.90, futuro);
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(naRota));

        List<Coordinates> rota = List.of(
                new Coordinates(-22.90, -43.30),
                new Coordinates(-22.85, -42.90), // exatamente sobre o relato
                new Coordinates(-22.80, -42.50));

        List<TrafficReport> cruzados = service.findNearRoute(rota);

        assertEquals(1, cruzados.size());
        assertTrue(cruzados.contains(naRota));
    }

    @Test
    void findNearRouteExcludesReportsFarFromEveryPoint() {
        TrafficReportService service = newService();
        Instant futuro = Instant.now().plusSeconds(600);
        TrafficReport longe = reportAt(TrafficSeverity.LEVE, -23.50, -46.60, futuro);
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -43.30), new Coordinates(-22.80, -42.50));

        List<TrafficReport> cruzados = service.findNearRoute(rota);

        assertTrue(cruzados.isEmpty());
    }
}
