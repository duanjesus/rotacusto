package com.rotacusto.dto.request;

import com.rotacusto.entity.enums.TipoEnergia;
import com.rotacusto.entity.enums.VehicleType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record VehicleProfileRequestDTO(
        @NotNull VehicleType tipo,
        @NotNull TipoEnergia tipoEnergia,
        @Positive double consumoPorUnidade,
        @Positive int numeroEixos,
        @PositiveOrZero double custoDesgastePorKm) {
}
