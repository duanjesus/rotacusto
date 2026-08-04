package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.TipoCombustivel;

public record FuelPriceResponseDTO(String uf, String municipio, TipoCombustivel tipoCombustivel, Double precoMedio,
        String semanaReferencia) {
}
