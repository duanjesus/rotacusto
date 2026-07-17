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
import org.springframework.transaction.annotation.Transactional;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.entity.RoadAlertVote;
import com.rotacusto.entity.enums.RoadAlertType;
import com.rotacusto.exception.DuplicateVoteException;
import com.rotacusto.exception.ResourceNotFoundException;
import com.rotacusto.repository.RoadAlertRepository;
import com.rotacusto.repository.RoadAlertVoteRepository;

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
    private final RoadAlertVoteRepository voteRepository;
    private final double detectionRadiusKm;
    private final long negativeVotesToExpire;

    public RoadAlertService(
            RoadAlertRepository repository,
            RoadAlertVoteRepository voteRepository,
            @Value("${rotacusto.road-alerts.detection-radius-km}") double detectionRadiusKm,
            @Value("${rotacusto.road-alerts.negative-votes-to-expire}") long negativeVotesToExpire) {
        this.repository = repository;
        this.voteRepository = voteRepository;
        this.detectionRadiusKm = detectionRadiusKm;
        this.negativeVotesToExpire = negativeVotesToExpire;
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

    /**
     * Confirmação/reputação (Fase 6.8) — "ainda está lá?" (confirma=true) estende
     * {@code expiraEm} pela mesma duração padrão do tipo, como se o alerta tivesse
     * acabado de ser reportado de novo. "já foi resolvido" (confirma=false) não expira
     * na hora (um voto isolado não devia matar um relato real) — acumula até
     * {@link #negativeVotesToExpire} votos negativos de dispositivos distintos, e só aí
     * {@code expiraEm} vira agora mesmo, reaproveitando o mecanismo de visibilidade que
     * {@code findNearPoint}/{@code findNearRoute} já filtram por padrão. Um dispositivo
     * só vota uma vez por alerta (constraint única em {@link RoadAlertVote}).
     */
    public RoadAlert vote(Long alertId, String deviceId, boolean confirma) {
        RoadAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado: " + alertId));
        if (voteRepository.existsByRoadAlertIdAndDeviceId(alertId, deviceId)) {
            throw new DuplicateVoteException("Este dispositivo já votou neste alerta.");
        }

        Instant agora = Instant.now();
        RoadAlertVote vote = new RoadAlertVote();
        vote.setRoadAlert(alert);
        vote.setDeviceId(deviceId);
        vote.setConfirmou(confirma);
        vote.setCriadoEm(agora);
        voteRepository.save(vote);

        if (confirma) {
            alert.setExpiraEm(agora.plus(DURACAO_PADRAO.get(alert.getTipo())));
        } else {
            long negativos = voteRepository.countByRoadAlertIdAndConfirmou(alertId, false);
            if (negativos >= negativeVotesToExpire) {
                alert.setExpiraEm(agora);
            }
        }
        return repository.save(alert);
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
     * correção (as buscas já filtram por expiraEm), só armazenamento.
     * {@code @Transactional} é obrigatório aqui — um {@code @Modifying} de
     * JPQL (DELETE em lote) exige transação, e métodos {@code @Scheduled}
     * não ganham uma automaticamente (achado só rodando com Spring de
     * verdade — Mockito não reclama, porque mock não liga pra transação). */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void limparExpirados() {
        int antesDoLimite = repository.findAll().size();
        // Precisa apagar os votos primeiro — bulk DELETE via JPQL não segue cascade de
        // anotação JPA, só uma FK de verdade no banco (que não configuramos por
        // simplicidade), então apagar o alerta antes quebraria a FK se ainda houvesse
        // votos referenciando ele.
        voteRepository.deleteByRoadAlertExpiraEmBefore(Instant.now());
        repository.deleteByExpiraEmBefore(Instant.now());
        log.info("Limpeza de alertas expirados: {} linha(s) verificada(s)", antesDoLimite);
    }
}
