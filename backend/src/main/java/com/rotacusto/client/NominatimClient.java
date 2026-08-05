package com.rotacusto.client;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.AddressSuggestion;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.ReverseGeocodeResult;
import com.rotacusto.exception.AddressNotFoundException;
import com.rotacusto.util.EstadoUtils;

/**
 * Client do Nominatim (geocoding OSM). Endpoint público exige um User-Agent
 * identificável e uso moderado (~1 req/s) — ver rotacusto.geocoding.* em application.yml.
 */
@Component
public class NominatimClient {

    private final RestClient restClient;

    public NominatimClient(
            @Value("${rotacusto.geocoding.base-url}") String baseUrl,
            @Value("${rotacusto.geocoding.user-agent}") String userAgent) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    public List<AddressSuggestion> search(String query, int limit) {
        List<JsonNode> results = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("limit", limit)
                        .queryParam("countrycodes", "br")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<JsonNode>>() {
                });

        if (results == null) {
            return List.of();
        }
        return results.stream()
                .map(n -> new AddressSuggestion(
                        n.path("display_name").asText(),
                        n.path("lat").asDouble(),
                        n.path("lon").asDouble()))
                .toList();
    }

    public Coordinates geocode(String address) {
        List<AddressSuggestion> results = search(address, 1);
        if (results.isEmpty()) {
            throw new AddressNotFoundException("Endereço não encontrado: " + address);
        }
        AddressSuggestion first = results.get(0);
        return new Coordinates(first.lat(), first.lon());
    }

    /**
     * Reverse geocoding (coordenada → cidade/estado), usado pra descobrir o
     * município de um posto de combustível candidato (Fase 15). {@code zoom=10}
     * (nível cidade) é deliberado — o default do Nominatim mira nível-prédio, que
     * frequentemente não preenche {@code address.city} pra um ponto isolado numa
     * rodovia (posto sem endereço de prédio ali); pedir granularidade de cidade
     * explicitamente aumenta a taxa de acerto. Retorna vazio se a resposta não
     * trouxer nenhum campo de cidade utilizável — deixa exceção de rede subir
     * pro chamador decidir o fallback (não é papel do client engolir isso).
     */
    public Optional<ReverseGeocodeResult> reverseGeocode(double lat, double lon) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/reverse")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", 1)
                        .queryParam("zoom", 10)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode address = response.path("address");
        String municipio = firstNonNull(address, "city", "town", "village", "municipality");
        if (municipio == null) {
            return Optional.empty();
        }
        String uf = EstadoUtils.siglaPorNomeCompleto(address.path("state").asText(null));
        return Optional.of(new ReverseGeocodeResult(municipio, uf));
    }

    private String firstNonNull(JsonNode address, String... campos) {
        for (String campo : campos) {
            String valor = address.path(campo).asText(null);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }
}
