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
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.entity.enums.RoadAlertType;
import com.rotacusto.repository.RoadAlertRepository;

@ExtendWith(MockitoExtension.class)
class RoadAlertServiceTest {

    @Mock
    private RoadAlertRepository repository;

    private RoadAlertService newService() {
        return new RoadAlertService(repository, 2.0);
    }

    private RoadAlert alertAt(RoadAlertType tipo, double lat, double lng, Instant expiraEm) {
        RoadAlert a = new RoadAlert();
        a.setTipo(tipo);
        a.setLat(lat);
        a.setLng(lng);
        a.setCriadoEm(Instant.now());
        a.setExpiraEm(expiraEm);
        return a;
    }

    @Test
    void reportComputesExpirationFromTypeDefaultDuration() {
        RoadAlertService service = newService();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoadAlert blitz = service.report(RoadAlertType.BLITZ, -22.9, -43.1);
        RoadAlert buraco = service.report(RoadAlertType.BURACO, -22.9, -43.1);

        long horasBlitz = ChronoUnit.HOURS.between(blitz.getCriadoEm(), blitz.getExpiraEm());
        long diasBuraco = ChronoUnit.DAYS.between(buraco.getCriadoEm(), buraco.getExpiraEm());
        assertEquals(2, horasBlitz, "blitz é um evento transitório, expira rápido");
        assertEquals(30, diasBuraco, "buraco só some quando a prefeitura resolve, expira devagar");
    }

    @Test
    void findNearPointOnlyReturnsAlertsWithinRadius() {
        RoadAlertService service = newService();
        Instant futuro = Instant.now().plusSeconds(3600);
        RoadAlert perto = alertAt(RoadAlertType.BURACO, -22.90, -43.10, futuro);
        RoadAlert longe = alertAt(RoadAlertType.BURACO, -23.50, -46.60, futuro); // São Paulo, bem longe
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(perto, longe));

        List<RoadAlert> encontrados = service.findNearPoint(new Coordinates(-22.90, -43.10), 5.0);

        assertEquals(1, encontrados.size());
        assertEquals(perto, encontrados.get(0));
    }

    @Test
    void findNearRouteMatchesAnyPointAlongTheRoute() {
        RoadAlertService service = newService();
        Instant futuro = Instant.now().plusSeconds(3600);
        RoadAlert naRota = alertAt(RoadAlertType.NEBLINA, -22.85, -42.90, futuro);
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(naRota));

        List<Coordinates> rota = List.of(
                new Coordinates(-22.90, -43.30),
                new Coordinates(-22.85, -42.90), // exatamente sobre o alerta
                new Coordinates(-22.80, -42.50));

        List<RoadAlert> cruzados = service.findNearRoute(rota);

        assertEquals(1, cruzados.size());
        assertTrue(cruzados.contains(naRota));
    }

    @Test
    void findNearRouteExcludesAlertsFarFromEveryPoint() {
        RoadAlertService service = newService();
        Instant futuro = Instant.now().plusSeconds(3600);
        RoadAlert longe = alertAt(RoadAlertType.ACIDENTE, -23.50, -46.60, futuro);
        when(repository.findByExpiraEmAfter(any())).thenReturn(List.of(longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -43.30), new Coordinates(-22.80, -42.50));

        List<RoadAlert> cruzados = service.findNearRoute(rota);

        assertTrue(cruzados.isEmpty());
    }
}
