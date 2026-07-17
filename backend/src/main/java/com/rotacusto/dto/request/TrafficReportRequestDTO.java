package com.rotacusto.dto.request;

import com.rotacusto.entity.enums.TrafficSeverity;

import jakarta.validation.constraints.NotNull;

public record TrafficReportRequestDTO(
        @NotNull TrafficSeverity severidade,
        @NotNull Double lat,
        @NotNull Double lng) {
}
