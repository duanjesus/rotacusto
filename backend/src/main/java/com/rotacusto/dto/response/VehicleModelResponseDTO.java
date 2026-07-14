package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.VehicleType;

public class VehicleModelResponseDTO {

    private Long id;
    private String marca;
    private String modelo;
    private Integer ano;
    private VehicleType tipo;
    private Double consumoCidadeKmL;
    private Double consumoEstradaKmL;
    private Integer numeroEixos;
    private Double custoDesgastePorKm;

    public VehicleModelResponseDTO() {
    }

    public VehicleModelResponseDTO(Long id, String marca, String modelo, Integer ano, VehicleType tipo,
            Double consumoCidadeKmL, Double consumoEstradaKmL, Integer numeroEixos, Double custoDesgastePorKm) {
        this.id = id;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
        this.tipo = tipo;
        this.consumoCidadeKmL = consumoCidadeKmL;
        this.consumoEstradaKmL = consumoEstradaKmL;
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

    public Double getConsumoCidadeKmL() {
        return consumoCidadeKmL;
    }

    public Double getConsumoEstradaKmL() {
        return consumoEstradaKmL;
    }

    public Integer getNumeroEixos() {
        return numeroEixos;
    }

    public Double getCustoDesgastePorKm() {
        return custoDesgastePorKm;
    }
}
