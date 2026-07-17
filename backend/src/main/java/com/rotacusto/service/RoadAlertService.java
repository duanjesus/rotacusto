package com.rotacusto.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.entity.enums.RoadAlertType;
import com.rotacusto.repository.RoadAlertRepository;

/**
 * Alertas de trânsito reportados por qualquer usuário (Fase 6.6), sem exigir
 * login — decisão de escopo confirmada com o usuário, mantém a filosofia do
 * app inteiro (login nunca trava funcionalidade principal). Sem
 * confirmação/reputação ("ainda está lá?"): cada tipo tem uma duração padrão
 * de validade, um chute razoável sem fonte de dado real (mesmo espírito de
 * honestidade já usado no custoDesgastePorKm do catálogo de veículos) —
 * BLITZ/CARRO_QUEBRADO/ACIDENTE são eventos transitórios que resolvem rápido,
 * NEBLINA depende do clima do dia, OBRA_NA_VIA e BURACO só somem quando o
 * problema físico é de fato resolvido.
 */
@Service
public class RoadAlertService {

    private static final Logger log = LoggerFactory.getLogger(RoadAlertService.class);

    private static final Map<RoadAlertType, Duration> DURACAO_PADRAO = Map.of(
            RoadAlertType.BLITZ, Duration.ofHours(2),
            RoadAlertType.CARRO_QUEBRADO, Duration.ofHours(2),
            RoadAlertType.ACIDENTE, Duration.ofHours(3),
            RoadAlertType.NEBLINA, Duration.ofHours(4),
            RoadAlertType.OBRA_NA_VIA, Duration.ofDays(14),
            RoadAlertType.BURACO, Duration.ofDays(30));

    private final RoadAlertRepository repository;
    private final double detectionRadiusKm;

    public RoadAlertService(
            RoadAlertRepository repository,
            @Value("${rotacusto.road-alerts.detection-radius-km}") double detectionRadiusKm) {
        this.repository = repository;
        this.detectionRadiusKm = detectionRadiusKm;
    }

    public RoadAlert report(RoadAlertType tipo, double lat, double lng) {
        Instant agora = Instant.now();
        RoadAlert alert = new RoadAlert();
        alert.setTipo(tipo);
        alert.setLat(lat);
        alert.setLng(lng);
        alert.setCriadoEm(agora);
        alert.setExpiraEm(agora.plus(DURACAO_PADRAO.get(tipo)));
        return repository.save(alert);
    }

    public List<RoadAlert> findNearPoint(Coordinates ponto, double raioKm) {
        return repository.findByExpiraEmAfter(Instant.now()).stream()
                .filter(a -> HaversineDistance.km(new Coordinates(a.getLat(), a.getLng()), ponto) <= raioKm)
                .toList();
    }

    /** Usa o raio padrão configurado quando quem chama não especifica um. */
    public List<RoadAlert> findNearPoint(Coordinates ponto) {
        return findNearPoint(ponto, detectionRadiusKm);
    }

    public List<RoadAlert> findNearRoute(List<Coordinates> geometriaRota) {
        List<RoadAlert> ativos = repository.findByExpiraEmAfter(Instant.now());
        return ativos.stream()
                .filter(a -> geometriaRota.stream()
                        .anyMatch(p -> HaversineDistance.km(p, new Coordinates(a.getLat(), a.getLng())) <= detectionRadiusKm))
                .toList();
    }

    /** Higiene simples — o banco agora é persistente (Fase 6.4a), então
     * linhas expiradas se acumulariam pra sempre sem isso. Não afeta
     * correção (as buscas já filtram por expiraEm), só armazenamento. */
    @Scheduled(cron = "0 0 4 * * *")
    public void limparExpirados() {
        int antesDoLimite = repository.findAll().size();
        repository.deleteByExpiraEmBefore(Instant.now());
        log.info("Limpeza de alertas expirados: {} linha(s) verificada(s)", antesDoLimite);
    }
}
