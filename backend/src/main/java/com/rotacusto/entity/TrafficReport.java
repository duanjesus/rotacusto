package com.rotacusto.entity;

import java.time.Instant;

import com.rotacusto.entity.enums.TrafficSeverity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Relato de trânsito lento (Fase 6.7), reportado automaticamente pelo próprio
 * app — não pelo usuário tocando num botão como {@link RoadAlert} (Fase 6.6)
 * — ao detectar que a velocidade GPS ao vivo está bem abaixo da velocidade
 * esperada do passo atual da rota (ver TrafficDetector no front-end). Sem
 * login, mesma filosofia do resto do app. Diferente de RoadAlert, não existe
 * "tipo" com duração física diferente por categoria — um engarrafamento é um
 * engarrafamento, sempre passa rápido — por isso {@code expiraEm} usa um TTL
 * único e curto (ver TrafficReportService), não um mapa por tipo.
 */
@Entity
@Table(name = "traffic_reports")
public class TrafficReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrafficSeverity severidade;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    public TrafficReport() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrafficSeverity getSeveridade() {
        return severidade;
    }

    public void setSeveridade(TrafficSeverity severidade) {
        this.severidade = severidade;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Instant expiraEm) {
        this.expiraEm = expiraEm;
    }
}
