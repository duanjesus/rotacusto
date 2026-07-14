package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.TipoEnergia;
import com.rotacusto.entity.enums.VehicleType;

public class VehicleModelResponseDTO {

    private Long id;
    private String marca;
    private String modelo;
    private Integer ano;
    private VehicleType tipo;
    private TipoEnergia tipoEnergia;
    private Double consumoCidadeKmL;
    private Double consumoEstradaKmL;
    private Double consumoKmPorKWh;
    private Integer numeroEixos;
    private Double custoDesgastePorKm;

    public VehicleModelResponseDTO() {
    }

    public VehicleModelResponseDTO(Long id, String marca, String modelo, Integer ano, VehicleType tipo,
            TipoEnergia tipoEnergia, Double consumoCidadeKmL, Double consumoEstradaKmL, Double consumoKmPorKWh,
            Integer numeroEixos, Double custoDesgastePorKm) {
        this.id = id;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
        this.tipo = tipo;
        this.tipoEnergia = tipoEnergia;
        this.consumoCidadeKmL = consumoCidadeKmL;
        this.consumoEstradaKmL = consumoEstradaKmL;
        this.consumoKmPorKWh = consumoKmPorKWh;
        this.numeroEixos = numeroEixos;
        this.custoDesgastePorKm = custoDesgastePorKm;
    }

    public Long getId() {
        return id;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public Integer getAno() {
        return ano;
    }

    public VehicleType getTipo() {
        return tipo;
    }

    public TipoEnergia getTipoEnergia() {
        return tipoEnergia;
    }

    public Double getConsumoCidadeKmL() {
        return consumoCidadeKmL;
    }

    public Double getConsumoEstradaKmL() {
        return consumoEstradaKmL;
    }

    public Double getConsumoKmPorKWh() {
        return consumoKmPorKWh;
    }

    public Integer getNumeroEixos() {
        return numeroEixos;
    }

    public Double getCustoDesgastePorKm() {
        return custoDesgastePorKm;
    }
}
