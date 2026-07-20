package com.rotacusto.entity;

import com.rotacusto.entity.enums.TipoCombustivel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Preço médio de combustível por UF, curado a partir do Levantamento de Preços
 * de Combustíveis da ANP (dado aberto semanal, Decreto 8.777/2016) — usado só
 * como valor SUGERIDO no formulário de viagem (o usuário sempre pode digitar
 * o preço real). Ver {@link com.rotacusto.config.FuelPriceSeeder} pra origem
 * e método de extração dos dados.
 */
@Entity
@Table(name = "fuel_prices")
public class FuelPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_combustivel", nullable = false)
    private TipoCombustivel tipoCombustivel;

    @Column(name = "preco_medio", nullable = false)
    private Double precoMedio;

    @Column(name = "semana_referencia", nullable = false)
    private String semanaReferencia;

    public FuelPrice() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public TipoCombustivel getTipoCombustivel() {
        return tipoCombustivel;
    }

    public void setTipoCombustivel(TipoCombustivel tipoCombustivel) {
        this.tipoCombustivel = tipoCombustivel;
    }

    public Double getPrecoMedio() {
        return precoMedio;
    }

    public void setPrecoMedio(Double precoMedio) {
        this.precoMedio = precoMedio;
    }

    public String getSemanaReferencia() {
        return semanaReferencia;
    }

    public void setSemanaReferencia(String semanaReferencia) {
        this.semanaReferencia = semanaReferencia;
    }
}
