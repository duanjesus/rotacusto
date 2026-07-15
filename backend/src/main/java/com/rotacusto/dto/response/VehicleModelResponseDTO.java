package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

public class VehicleModelResponseDTO {

    private Long id;
    private String marca;
    private String modelo;
    private Integer ano;
    private VehicleType tipo;
    private TipoCombustivel tipoCombustivel;
    private Double consumoCidadeKmL;
    private Double consumoEstradaKmL;
    private Double consumoKmPorKWh;
    private Integer numeroEixos;
    private Double custoDesgastePorKm;
    private Integer cilindradaCC;
    private Integer pbtKg;

    public VehicleModelResponseDTO() {
    }

    public VehicleModelResponseDTO(Long id, String marca, String modelo, Integer ano, VehicleType tipo,
            TipoCombustivel tipoCombustivel, Double consumoCidadeKmL, Double consumoEstradaKmL, Double consumoKmPorKWh,
            Integer numeroEixos, Double custoDesgastePorKm, Integer cilindradaCC, Integer pbtKg) {
        this.id = id;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
        this.tipo = tipo;
        this.tipoCombustivel = tipoCombustivel;
        this.consumoCidadeKmL = consumoCidadeKmL;
        this.consumoEstradaKmL = consumoEstradaKmL;
        this.consumoKmPorKWh = consumoKmPorKWh;
        this.numeroEixos = numeroEixos;
        this.custoDesgastePorKm = custoDesgastePorKm;
        this.cilindradaCC = cilindradaCC;
        this.pbtKg = pbtKg;
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

    public TipoCombustivel getTipoCombustivel() {
        return tipoCombustivel;
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

    public Integer getCilindradaCC() {
        return cilindradaCC;
    }

    public Integer getPbtKg() {
        return pbtKg;
    }
}
