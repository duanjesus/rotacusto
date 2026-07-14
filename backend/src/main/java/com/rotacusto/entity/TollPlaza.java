package com.rotacusto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "toll_plazas")
public class TollPlaza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String rodovia;

    @Column(nullable = false)
    private String concessionaria;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** Tarifa por eixo (R$). Carro (2 eixos) paga tarifaPorEixo * 2; caminhão/ônibus escalam pelo nº de eixos. */
    @Column(name = "tarifa_por_eixo", nullable = false)
    private Double tarifaPorEixo;

    /** Tarifa fixa para moto, quando divulgada pela concessionária (categoria própria, não segue o eixo). */
    @Column(name = "tarifa_moto")
    private Double tarifaMoto;

    /**
     * Praças de sentido único (comuns no Brasil — a cabine física só existe
     * numa das pistas). Quando refLat/refLng estão preenchidos, a praça só é
     * cobrada se a rota estiver indo em direção a esse ponto de referência
     * (cobraApenasIndo=true) ou se afastando dele (cobraApenasIndo=false).
     * Nulo = cobra nos dois sentidos (padrão da maioria das praças).
     */
    @Column(name = "ref_lat")
    private Double refLat;

    @Column(name = "ref_lng")
    private Double refLng;

    @Column(name = "cobra_apenas_indo")
    private Boolean cobraApenasIndo;

    public TollPlaza() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getRodovia() {
        return rodovia;
    }

    public void setRodovia(String rodovia) {
        this.rodovia = rodovia;
    }

    public String getConcessionaria() {
        return concessionaria;
    }

    public void setConcessionaria(String concessionaria) {
        this.concessionaria = concessionaria;
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

    public Double getTarifaPorEixo() {
        return tarifaPorEixo;
    }

    public void setTarifaPorEixo(Double tarifaPorEixo) {
        this.tarifaPorEixo = tarifaPorEixo;
    }

    public Double getTarifaMoto() {
        return tarifaMoto;
    }

    public void setTarifaMoto(Double tarifaMoto) {
        this.tarifaMoto = tarifaMoto;
    }

    public Double getRefLat() {
        return refLat;
    }

    public void setRefLat(Double refLat) {
        this.refLat = refLat;
    }

    public Double getRefLng() {
        return refLng;
    }

    public void setRefLng(Double refLng) {
        this.refLng = refLng;
    }

    public Boolean getCobraApenasIndo() {
        return cobraApenasIndo;
    }

    public void setCobraApenasIndo(Boolean cobraApenasIndo) {
        this.cobraApenasIndo = cobraApenasIndo;
    }
}
