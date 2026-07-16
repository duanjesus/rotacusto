package com.rotacusto.dto.response;

import java.time.Instant;

public record TripHistoryDetailDTO(
        String origem,
        String destino,
        Instant calculadoEm,
        TripCostBreakdownDTO breakdown) {
}
