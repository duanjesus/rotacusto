package com.rotacusto.entity;

import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle_models")
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType tipo;

    /**
     * Um veículo flex vira DUAS linhas de catálogo (mesmo marca/modelo/ano,
     * GASOLINA e ETANOL cada uma com seu consumo) — combustível é parte da
     * identidade do registro, não um detalhe do veículo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_combustivel", nullable = false)
    private TipoCombustivel tipoCombustivel;

    /** Nulo quando tipoCombustivel = ELETRICO. */
    @Column(name = "consumo_cidade_km_l")
    private Double consumoCidadeKmL;

    @Column(name = "consumo_estrada_km_l")
    private Double consumoEstradaKmL;

    /** Só preenchido quando tipoCombustivel = ELETRICO. */
    @Column(name = "consumo_km_por_kwh")
    private Double consumoKmPorKWh;

    @Column(name = "numero_eixos", nullable = false)
    private Integer numeroEixos;

    @Column(name = "custo_desgaste_por_km", nullable = false)
    private Double custoDesgastePorKm;

    public VehicleModel() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public VehicleType getTipo() {
        return tipo;
    }

    public void setTipo(VehicleType tipo) {
        this.tipo = tipo;
    }

    public Double getConsumoCidadeKmL() {
        return consumoCidadeKmL;
    }

    public void setConsumoCidadeKmL(Double consumoCidadeKmL) {
        this.consumoCidadeKmL = consumoCidadeKmL;
    }

    public Double getConsumoEstradaKmL() {
        return consumoEstradaKmL;
    }

    public void setConsumoEstradaKmL(Double consumoEstradaKmL) {
        this.consumoEstradaKmL = consumoEstradaKmL;
    }

    public TipoCombustivel getTipoCombustivel() {
        return tipoCombustivel;
    }

    public void setTipoCombustivel(TipoCombustivel tipoCombustivel) {
        this.tipoCombustivel = tipoCombustivel;
    }

    public Double getConsumoKmPorKWh() {
        return consumoKmPorKWh;
    }

    public void setConsumoKmPorKWh(Double consumoKmPorKWh) {
        this.consumoKmPorKWh = consumoKmPorKWh;
    }

    public Integer getNumeroEixos() {
        return numeroEixos;
    }

    public void setNumeroEixos(Integer numeroEixos) {
        this.numeroEixos = numeroEixos;
    }

    public Double getCustoDesgastePorKm() {
        return custoDesgastePorKm;
    }

    public void setCustoDesgastePorKm(Double custoDesgastePorKm) {
        this.custoDesgastePorKm = custoDesgastePorKm;
    }
}
