package com.rotacusto.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        JsonNode response = requestDirections(waypoints, false, false);
        return parseFeature(response.path("features").get(0));
    }

    /**
     * Rotas alternativas (Fase 10) — só funciona pra exatamente 2 waypoints
     * (origem+destino); o ORS não oferece {@code alternative_routes} com
     * waypoints intermediários. Pode devolver só 1 rota se não achar nenhuma
     * alternativa genuinamente diferente — não é erro, é esperado.
     *
     * <p>Combina duas fontes de alternativa, porque nenhuma sozinha cobre o Brasil
     * inteiro: (1) {@code alternative_routes} do próprio ORS, que devolve caminhos
     * geometricamente distintos mas, achado real testando contra a API, **rejeita
     * com 400 quando a distância aproximada passa de 100km** ("Request parameters
     * exceed the server configuration limits") — o algoritmo não escala pra viagens
     * intermunicipais/interestaduais, que são justamente onde pedágio pesa mais no
     * custo; e (2) uma segunda chamada pedindo pra **evitar pedágios**
     * ({@code avoid_features: tollways}), que funciona em qualquer distância (é só
     * uma rota normal com uma preferência diferente, não usa o algoritmo limitado) e
     * é uma alternativa que já é diretamente relevante pro que este app calcula —
     * só entra na lista se a distância vier de fato diferente da(s) rota(s) já
     * coletada(s); se a rota "sem pedágio" volta idêntica (rota original já não
     * cruzava nenhum), não é adicionada como alternativa falsa.
     */
    public List<RouteResult> getRoutes(List<Coordinates> waypoints) {
        if (waypoints.size() != 2) {
            throw new IllegalArgumentException("Rotas alternativas só funcionam com origem+destino, sem paradas.");
        }

        List<RouteResult> rotas = new ArrayList<>();
        try {
            JsonNode response = requestDirections(waypoints, true, false);
            for (JsonNode feature : response.path("features")) {
                rotas.add(parseFeature(feature));
            }
        } catch (HttpClientErrorException.BadRequest e) {
            rotas.add(getRoute(waypoints));
        }

        try {
            JsonNode semPedagio = requestDirections(waypoints, false, true);
            RouteResult rotaSemPedagio = parseFeature(semPedagio.path("features").get(0));
            boolean jaTemRotaParecida = rotas.stream()
                    .anyMatch(r -> Math.abs(r.distanciaKm() - rotaSemPedagio.distanciaKm()) < 0.5);
            if (!jaTemRotaParecida) {
                rotas.add(rotaSemPedagio);
            }
        } catch (RestClientException e) {
            // Opcional — se essa chamada falhar (rede/ORS instável), as rotas já
            // coletadas acima (ou nenhuma, degradando pra rota única em TripEstimationService)
            // continuam válidas; não vale derrubar o endpoint inteiro por uma tentativa extra.
        }

        return rotas;
    }

    private JsonNode requestDirections(List<Coordinates> waypoints, boolean alternatives, boolean avoidTollways) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "ORS_API_KEY não configurada. Cadastre-se em openrouteservice.org e defina a variável de ambiente ORS_API_KEY.");
        }

        List<List<Double>> coordinates = waypoints.stream()
                .map(c -> List.of(c.lon(), c.lat()))
                .toList();

        Map<String, Object> body = new java.util.HashMap<>(Map.of("coordinates", coordinates, "instructions", true));
        if (alternatives) {
            // target_count 2 = até 2 rotas ALÉM da principal (3 no total). weight_factor/
            // share_factor são os valores sugeridos na documentação do ORS pra alternativas
            // razoavelmente diferentes da rota principal (não 3 variações quase idênticas).
            body.put("alternative_routes", Map.of("target_count", 2, "weight_factor", 1.6, "share_factor", 0.6));
        }
        if (avoidTollways) {
            body.put("options", Map.of("avoid_features", List.of("tollways")));
        }

        // POST em vez de GET: o endpoint GET /v2/directions/driving-car só aceita
        // exatamente 2 pontos (start/end) — passar por paradas intermediárias exige
        // o endpoint /geojson com o array "coordinates" no corpo. Autenticação por
        // header aqui (o GET usa "api_key" como query param, mas o corpo POST usa
        // "Authorization" — convenção da própria API do ORS, não deste projeto).
        return restClient.post()
                .uri("/v2/directions/driving-car/geojson")
                .header("Authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private RouteResult parseFeature(JsonNode feature) {
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
