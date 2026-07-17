package com.rotacusto.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Voto de confirmação/reputação num {@link RoadAlert} (Fase 6.8) — "ainda está lá?"
 * (confirmou=true, estende a validade do alerta) ou "já foi resolvido"
 * (confirmou=false, acumula até um limiar que expira o alerta na hora). Sem login: o
 * dispositivo se identifica por um UUID anônimo gerado e salvo localmente pelo app,
 * nunca vinculado a conta nenhuma — a constraint única em (road_alert_id, device_id)
 * garante um voto por dispositivo por alerta.
 */
@Entity
@Table(name = "road_alert_votes", uniqueConstraints = @UniqueConstraint(columnNames = { "road_alert_id", "device_id" }))
public class RoadAlertVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "road_alert_id", nullable = false)
    private RoadAlert roadAlert;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private boolean confirmou;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public RoadAlertVote() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RoadAlert getRoadAlert() {
        return roadAlert;
    }

    public void setRoadAlert(RoadAlert roadAlert) {
        this.roadAlert = roadAlert;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isConfirmou() {
        return confirmou;
    }

    public void setConfirmou(boolean confirmou) {
        this.confirmou = confirmou;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
