package com.rotacusto.entity;

import java.time.Instant;

import com.rotacusto.entity.enums.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pedido de "não achei meu veículo no catálogo", enviado direto do app, sem login. */
@Entity
@Table(name = "vehicle_reports")
public class VehicleReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType tipo;

    // TEXT e não @Lob: @Lob em String vira large object (OID) no Postgres e quebra
    // leitura fora de transação — mesmo motivo de TripHistoryEntry.breakdownJson.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public VehicleReport() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VehicleType getTipo() {
        return tipo;
    }

    public void setTipo(VehicleType tipo) {
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
