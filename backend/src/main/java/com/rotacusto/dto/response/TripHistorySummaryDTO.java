package com.rotacusto.dto.response;

import java.time.Instant;

public record TripHistorySummaryDTO(
        Long id,
        String origem,
        String destino,
        Double distanciaKm,
        Double total,
        Instant calculadoEm) {
}
