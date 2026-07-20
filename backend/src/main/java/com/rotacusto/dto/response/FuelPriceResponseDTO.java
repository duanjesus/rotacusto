package com.rotacusto.dto.response;

import com.rotacusto.entity.enums.TipoCombustivel;

public record FuelPriceResponseDTO(String uf, TipoCombustivel tipoCombustivel, Double precoMedio,
        String semanaReferencia) {
}
