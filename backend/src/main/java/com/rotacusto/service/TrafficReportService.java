package com.rotacusto.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.TrafficReport;
import com.rotacusto.entity.enums.TrafficSeverity;
import com.rotacusto.repository.TrafficReportRepository;

/**
 * Relatos de trânsito lento (Fase 6.7), gerados automaticamente pelo app do
 * usuário — não por um botão como {@link RoadAlertService} (Fase 6.6) — ao
 * comparar a velocidade GPS ao vivo com a velocidade esperada do trecho da
 * rota (ver TrafficDetector no front-end). Sem login, mesma filosofia do app
 * inteiro. TTL único e curto (não um mapa por tipo como em RoadAlertService):
 * diferente de um alerta de buraco/obra, um engarrafamento não tem
 * "categoria" com persistência física diferente — sempre é uma condição
 * transitória.
 */
@Service
public class TrafficReportService {

    private static final Logger log = LoggerFactory.getLogger(TrafficReportService.class);

    private final TrafficReportRepository repository;
    private final double detectionRadiusKm;
    private final Duration ttl;

    public TrafficReportService(
            TrafficReportRepository repository,
            @Value("${rotacusto.traffic-reports.detection-radius-km}") double detectionRadiusKm,
            @Value("${rotacusto.traffic-reports.ttl-minutes}") long ttlMinutes) {
        this.repository = repository;
        this.detectionRadiusKm = detectionRadiusKm;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public TrafficReport report(TrafficSeverity severidade, double lat, double lng) {
        Instant agora = Instant.now();
        TrafficReport relato = new TrafficReport();
        relato.setSeveridade(severidade);
        relato.setLat(lat);
        relato.setLng(lng);
        relato.setCriadoEm(agora);
        relato.setExpiraEm(agora.plus(ttl));
        return repository.save(relato);
    }

    public List<TrafficReport> findNearPoint(Coordinates ponto, double raioKm) {
        return repository.findByExpiraEmAfter(Instant.now()).stream()
                .filter(r -> HaversineDistance.km(new Coordinates(r.getLat(), r.getLng()), ponto) <= raioKm)
                .toList();
    }

    /** Usa o raio padrão configurado quando quem chama não especifica um. */
    public List<TrafficReport> findNearPoint(Coordinates ponto) {
        return findNearPoint(ponto, detectionRadiusKm);
    }

    public List<TrafficReport> findNearRoute(List<Coordinates> geometriaRota) {
        List<TrafficReport> ativos = repository.findByExpiraEmAfter(Instant.now());
        return ativos.stream()
                .filter(r -> geometriaRota.stream()
                        .anyMatch(p -> HaversineDistance.km(p, new Coordinates(r.getLat(), r.getLng())) <= detectionRadiusKm))
                .toList();
    }

    /**
     * Limpeza bem mais frequente que a diária do {@link RoadAlertService} —
     * de propósito: o TTL aqui é de minutos, não de horas/dias, então uma
     * limpeza só às 4h deixaria linhas expiradas se acumulando o dia inteiro.
     * Não afeta correção (as buscas já filtram por expiraEm), só armazenamento.
     */
    @Scheduled(fixedRate = 600000)
    public void limparExpirados() {
        int antesDoLimite = repository.findAll().size();
        repository.deleteByExpiraEmBefore(Instant.now());
        log.info("Limpeza de relatos de trânsito expirados: {} linha(s) verificada(s)", antesDoLimite);
    }
}
