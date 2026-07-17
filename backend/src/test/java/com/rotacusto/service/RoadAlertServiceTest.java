package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.entity.enums.RoadAlertType;
import com.rotacusto.exception.DuplicateVoteException;
import com.rotacusto.exception.ResourceNotFoundException;
import com.rotacusto.repository.RoadAlertRepository;
import com.rotacusto.repository.RoadAlertVoteRepository;

@ExtendWith(MockitoExtension.class)
class RoadAlertServiceTest {

    @Mock
    private RoadAlertRepository repository;

    @Mock
    private RoadAlertVoteRepository voteRepository;

    private RoadAlertService newService() {
        return new RoadAlertService(repository, voteRepository, 2.0, 2);
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

    @Test
    void voteConfirmaExtendsExpirationByTypeDefaultDuration() {
        RoadAlertService service = newService();
        RoadAlert blitz = alertAt(RoadAlertType.BLITZ, -22.9, -43.1, Instant.now().plusSeconds(60));
        when(repository.findById(1L)).thenReturn(Optional.of(blitz));
        when(voteRepository.existsByRoadAlertIdAndDeviceId(1L, "device-a")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant antes = Instant.now();
        RoadAlert atualizado = service.vote(1L, "device-a", true);

        // Tolerância em vez de comparar horas truncadas: as duas chamadas a
        // Instant.now() (dentro do serviço e aqui) não são simultâneas, então um
        // ChronoUnit.HOURS.between exato flaca se cair bem na borda da hora.
        assertTrue(atualizado.getExpiraEm().isAfter(antes.plus(Duration.ofMinutes(119))),
                "confirmar re-aplica a duração padrão do tipo (BLITZ = 2h)");
        assertTrue(atualizado.getExpiraEm().isBefore(antes.plus(Duration.ofMinutes(121))),
                "não deveria estender além da duração padrão do tipo");
    }

    @Test
    void voteNegaBelowThresholdDoesNotExpireImmediately() {
        RoadAlertService service = newService();
        Instant futuro = Instant.now().plusSeconds(3600);
        RoadAlert buraco = alertAt(RoadAlertType.BURACO, -22.9, -43.1, futuro);
        when(repository.findById(1L)).thenReturn(Optional.of(buraco));
        when(voteRepository.existsByRoadAlertIdAndDeviceId(1L, "device-a")).thenReturn(false);
        when(voteRepository.countByRoadAlertIdAndConfirmou(1L, false)).thenReturn(1L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoadAlert atualizado = service.vote(1L, "device-a", false);

        assertEquals(futuro, atualizado.getExpiraEm(), "1 voto negativo, abaixo do limiar de 2, não expira");
    }

    @Test
    void voteNegaAtThresholdExpiresImmediately() {
        RoadAlertService service = newService();
        RoadAlert buraco = alertAt(RoadAlertType.BURACO, -22.9, -43.1, Instant.now().plusSeconds(3600));
        when(repository.findById(1L)).thenReturn(Optional.of(buraco));
        when(voteRepository.existsByRoadAlertIdAndDeviceId(1L, "device-b")).thenReturn(false);
        when(voteRepository.countByRoadAlertIdAndConfirmou(1L, false)).thenReturn(2L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoadAlert atualizado = service.vote(1L, "device-b", false);

        assertTrue(!atualizado.getExpiraEm().isAfter(Instant.now()),
                "atingindo o limiar de votos negativos, o alerta expira imediatamente");
    }

    @Test
    void voteFromSameDeviceTwiceThrows() {
        RoadAlertService service = newService();
        RoadAlert buraco = alertAt(RoadAlertType.BURACO, -22.9, -43.1, Instant.now().plusSeconds(3600));
        when(repository.findById(1L)).thenReturn(Optional.of(buraco));
        when(voteRepository.existsByRoadAlertIdAndDeviceId(1L, "device-a")).thenReturn(true);

        assertThrows(DuplicateVoteException.class, () -> service.vote(1L, "device-a", true));
    }

    @Test
    void voteOnMissingAlertThrowsNotFound() {
        RoadAlertService service = newService();
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.vote(99L, "device-a", true));
    }
}
