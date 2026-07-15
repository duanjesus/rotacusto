package com.rotacusto.dto.request;

import com.rotacusto.entity.enums.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleReportRequestDTO(@NotNull VehicleType tipo, @NotBlank String descricao) {
}
