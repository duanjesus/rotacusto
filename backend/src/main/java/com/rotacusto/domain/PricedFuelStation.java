package com.rotacusto.domain;

/**
 * Um posto candidato a "melhor preço" (Fase 15), já com o preço médio do
 * município (via reverse geocoding + {@link com.rotacusto.entity.FuelPrice})
 * anexado. {@code precoMedio}/{@code municipio} nulos = não foi possível
 * precificar esse posto (Nominatim falhou, ou nenhum município/UF bateu na
 * tabela ANP) — cai no fallback nearest-to-midpoint de sempre
 * ({@link com.rotacusto.service.FuelStationService#suggestStop}).
 */
public record PricedFuelStation(OsmFuelStation station, Double precoMedio, String municipio) {
}
