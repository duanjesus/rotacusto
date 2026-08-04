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

    /**
     * Nulo nas linhas de fallback por UF (média de todo o estado); preenchido
     * nas linhas específicas de município (Fase 14). Comparado sempre em forma
     * normalizada (maiúsculo, sem acento) pelo cliente — a fonte ANP já vem sem
     * acento (ex. "SAO PAULO"), então a comparação exata com o nome vindo do
     * Photon/Nominatim (que tem acento) não bateria.
     */
    @Column(nullable = true)
    private String municipio;

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

    public String getMunicipio() {
        return municipio;
    }

    public void setMunicipio(String municipio) {
        this.municipio = municipio;
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
