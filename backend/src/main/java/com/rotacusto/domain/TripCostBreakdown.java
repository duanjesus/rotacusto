package com.rotacusto.domain;

public record TripCostBreakdown(
        double distanciaKm,
        double duracaoMin,
        double custoCombustivel,
        double custoDesgaste,
        double custoPedagio,
        double custoLanche,
        double total) {
}
