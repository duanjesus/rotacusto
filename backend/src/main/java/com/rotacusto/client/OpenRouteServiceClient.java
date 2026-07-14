package com.rotacusto.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteResult;

/**
 * Client do OpenRouteService (roteamento sobre OSM). Requer uma API key gratuita
 * (ORS_API_KEY) — nunca exposta ao app, só usada aqui no back-end.
 */
@Component
public class OpenRouteServiceClient {

    private final RestClient restClient;
    private final String apiKey;

    public OpenRouteServiceClient(
            @Value("${rotacusto.routing.base-url}") String baseUrl,
            @Value("${rotacusto.routing.api-key}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public RouteResult getRoute(Coordinates origin, Coordinates destination) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "ORS_API_KEY não configurada. Cadastre-se em openrouteservice.org e defina a variável de ambiente ORS_API_KEY.");
        }

        String start = origin.lon() + "," + origin.lat();
        String end = destination.lon() + "," + destination.lat();

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/directions/driving-car")
                        .queryParam("api_key", apiKey)
                        .queryParam("start", start)
                        .queryParam("end", end)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode feature = response.path("features").get(0);
        JsonNode summary = feature.path("properties").path("summary");
        double distanciaKm = summary.path("distance").asDouble() / 1000.0;
        double duracaoMin = summary.path("duration").asDouble() / 60.0;

        List<Coordinates> geometria = new ArrayList<>();
        for (JsonNode coord : feature.path("geometry").path("coordinates")) {
            geometria.add(new Coordinates(coord.get(1).asDouble(), coord.get(0).asDouble()));
        }

        return new RouteResult(distanciaKm, duracaoMin, geometria);
    }
}
