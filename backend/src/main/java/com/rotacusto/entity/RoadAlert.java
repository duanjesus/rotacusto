package com.rotacusto.entity;

import java.time.Instant;

import com.rotacusto.entity.enums.RoadAlertType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Alerta de trânsito reportado por qualquer usuário do app (Fase 6.6), sem
 * exigir login — mantém a filosofia do app inteiro de nunca travar
 * funcionalidade principal atrás de conta. Sem confirmação/reputação
 * ("ainda está lá?"): {@code expiraEm} é calculado uma vez, na criação, a
 * partir de uma duração padrão por tipo (ver RoadAlertService) — passado
 * esse prazo, o alerta simplesmente para de aparecer nas buscas.
 */
@Entity
@Table(name = "road_alerts")
public class RoadAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoadAlertType tipo;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    public RoadAlert() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RoadAlertType getTipo() {
        return tipo;
    }

    public void setTipo(RoadAlertType tipo) {
        this.tipo = tipo;
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
