package com.rotacusto.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteResult;
import com.rotacusto.domain.RouteStep;
import com.rotacusto.domain.geo.ManeuverTranslator;

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

    /**
     * @param waypoints origem, zero ou mais paradas intermediárias, destino — nesta
     *                  ordem. Mínimo 2 elementos. O ORS passa pela lista inteira numa
     *                  rota única contínua (não são N chamadas separadas).
     */
    public RouteResult getRoute(List<Coordinates> waypoints) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "ORS_API_KEY não configurada. Cadastre-se em openrouteservice.org e defina a variável de ambiente ORS_API_KEY.");
        }

        List<List<Double>> coordinates = waypoints.stream()
                .map(c -> List.of(c.lon(), c.lat()))
                .toList();

        // POST em vez de GET: o endpoint GET /v2/directions/driving-car só aceita
        // exatamente 2 pontos (start/end) — passar por paradas intermediárias exige
        // o endpoint /geojson com o array "coordinates" no corpo. Autenticação por
        // header aqui (o GET usa "api_key" como query param, mas o corpo POST usa
        // "Authorization" — convenção da própria API do ORS, não deste projeto).
        JsonNode response = restClient.post()
                .uri("/v2/directions/driving-car/geojson")
                .header("Authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("coordinates", coordinates, "instructions", true))
                .retrieve()
                .body(JsonNode.class);

        JsonNode feature = response.path("features").get(0);
        JsonNode properties = feature.path("properties");
        JsonNode summary = properties.path("summary");
        double distanciaKm = summary.path("distance").asDouble() / 1000.0;
        double duracaoMin = summary.path("duration").asDouble() / 60.0;

        List<Coordinates> geometria = new ArrayList<>();
        for (JsonNode coord : feature.path("geometry").path("coordinates")) {
            geometria.add(new Coordinates(coord.get(1).asDouble(), coord.get(0).asDouble()));
        }

        List<RouteStep> passos = new ArrayList<>();
        for (JsonNode segment : properties.path("segments")) {
            for (JsonNode step : segment.path("steps")) {
                JsonNode wayPoints = step.path("way_points");
                String instrucao = ManeuverTranslator.translate(step.path("type").asInt(), step.path("name").asText());
                passos.add(new RouteStep(
                        instrucao,
                        step.path("distance").asDouble(),
                        step.path("duration").asDouble(),
                        wayPoints.get(0).asInt(),
                        wayPoints.get(1).asInt()));
            }
        }

        return new RouteResult(distanciaKm, duracaoMin, geometria, passos);
    }
}
