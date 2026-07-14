package com.rotacusto.dto.response;

public record TollPlazaResponseDTO(
        String nome,
        String rodovia,
        String concessionaria,
        double lat,
        double lng,
        double valorCobrado) {
}
