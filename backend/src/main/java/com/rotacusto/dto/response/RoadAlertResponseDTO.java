package com.rotacusto.dto.response;

import java.time.Instant;

import com.rotacusto.entity.enums.RoadAlertType;

public record RoadAlertResponseDTO(
        Long id,
        RoadAlertType tipo,
        Double lat,
        Double lng,
        Instant criadoEm,
        Instant expiraEm) {
}
