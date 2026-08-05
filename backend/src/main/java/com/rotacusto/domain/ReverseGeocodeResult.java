package com.rotacusto.domain;

/**
 * Resultado de reverse geocoding (coordenada → cidade/estado) via Nominatim
 * ({@link com.rotacusto.client.NominatimClient#reverseGeocode}). {@code municipio}
 * vem com acento (ex. "São Paulo") — comparar contra {@link com.rotacusto.entity.FuelPrice}
 * (sem acento) exige normalizar os dois lados, ver
 * {@link com.rotacusto.util.AccentUtils}.
 */
public record ReverseGeocodeResult(String municipio, String uf) {
}
