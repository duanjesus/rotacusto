package com.rotacusto.dto.request;

import com.rotacusto.entity.enums.RoadAlertType;

import jakarta.validation.constraints.NotNull;

public record RoadAlertRequestDTO(
        @NotNull RoadAlertType tipo,
        @NotNull Double lat,
        @NotNull Double lng) {
}
