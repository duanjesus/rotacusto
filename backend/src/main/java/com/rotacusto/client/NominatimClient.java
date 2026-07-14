package com.rotacusto.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.exception.AddressNotFoundException;

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

    public Coordinates geocode(String address) {
        List<JsonNode> results = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                        .queryParam("q", address)
                        .queryParam("format", "json")
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<JsonNode>>() {
                });

        if (results == null || results.isEmpty()) {
            throw new AddressNotFoundException("Endereço não encontrado: " + address);
        }

        JsonNode first = results.get(0);
        return new Coordinates(first.path("lat").asDouble(), first.path("lon").asDouble());
    }
}
