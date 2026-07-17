package com.rotacusto.dto.response;

import java.time.Instant;

import com.rotacusto.entity.enums.TrafficSeverity;

public record TrafficReportResponseDTO(
        Long id,
        TrafficSeverity severidade,
        Double lat,
        Double lng,
        Instant criadoEm,
        Instant expiraEm) {
}
