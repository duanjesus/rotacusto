package com.rotacusto.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.OsmRadar;
import com.rotacusto.domain.OsmTollBooth;
import com.rotacusto.entity.enums.RadarType;

/**
 * Client do Overpass API (dados ao vivo do OpenStreetMap). Usado para achar
 * praças de pedágio e postos de combustível reais dentro de uma área — cobre
 * o Brasil inteiro, ao contrário de um dataset fixo.
 */
@Component
public class OverpassClient {

    private final RestClient restClient;

    /**
     * O Overpass público às vezes fica sobrecarregado e demora bastante pra
     * responder (ou nem responde) antes de devolver 504 — sem timeout aqui,
     * uma consulta lenta poderia segurar a requisição inteira tempo demais
     * (o cliente desiste antes do back-end sequer cair pro fallback). Os dois
     * chamadores (TollService, FuelStationService) já tratam falha do
     * Overpass com try/catch + fallback; esse timeout é o que garante que
     * esse fallback aconteça rápido.
     */
    public OverpassClient(@Value("${rotacusto.overpass.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public List<OsmTollBooth> findTollBoothsInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        // Locale.ROOT é obrigatório aqui: %f usa o locale padrão da JVM, que em
        // pt-BR usa vírgula decimal e quebraria a sintaxe do Overpass QL.
        String query = String.format(Locale.ROOT, """
                [out:json][timeout:8][bbox:%f,%f,%f,%f];
                (
                  node["barrier"="toll_booth"];
                  way["highway"="toll_gantry"];
                );
                out center;
                """, minLat, minLon, maxLat, maxLon);

        JsonNode response = executeQuery(query);

        List<OsmTollBooth> result = new ArrayList<>();
        for (JsonNode el : response.path("elements")) {
            double[] coords = extractCoordinates(el);
            if (coords == null) {
                continue;
            }
            String nome = el.path("tags").path("name").asText(null);
            String rodovia = el.path("tags").path("ref").asText(null);
            result.add(new OsmTollBooth(
                    nome != null ? nome : "Pedágio (OpenStreetMap)",
                    rodovia != null ? rodovia : "N/D",
                    coords[0],
                    coords[1]));
        }
        return result;
    }

    public List<OsmFuelStation> findFuelStationsInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        String query = String.format(Locale.ROOT, """
                [out:json][timeout:8][bbox:%f,%f,%f,%f];
                (
                  node["amenity"="fuel"];
                  way["amenity"="fuel"];
                );
                out center;
                """, minLat, minLon, maxLat, maxLon);

        JsonNode response = executeQuery(query);

        List<OsmFuelStation> result = new ArrayList<>();
        for (JsonNode el : response.path("elements")) {
            double[] coords = extractCoordinates(el);
            if (coords == null) {
                continue;
            }
            JsonNode tags = el.path("tags");
            String nome = tags.path("name").asText(null);
            String marca = tags.path("brand").asText(null);
            String label = nome != null ? nome : (marca != null ? marca : "Posto de combustível (OpenStreetMap)");
            result.add(new OsmFuelStation(label, coords[0], coords[1]));
        }
        return result;
    }

    /**
     * Radares fixos (Fase 12/12.1) — infraestrutura permanente, ao contrário de alertas
     * reportados por usuário: não expira, não é votada, só é consultada ao vivo. Uma
     * query só cobre os dois tipos (evita 2 chamadas HTTP ao Overpass, que já é lento/
     * instável às vezes — ver comentário no construtor).
     *
     * <p>Radar de velocidade tem uma tag única e dominante ({@code highway=speed_camera}).
     * Radar de avanço de sinal **não tem** — a forma "correta" segundo o wiki do OSM é
     * uma relation {@code type=enforcement}+{@code enforcement=traffic_signals}, mas na
     * prática mapeadores também usam tags soltas direto no nó ({@code
     * highway=red_light_camera}, {@code enforcement=traffic_signals} fora de relation,
     * {@code man_made=redlight_camera}, {@code enforcement_camera=yes}). A query busca
     * todas as variantes pra maximizar cobertura — mesmo cobertura esparsa é esperada e
     * documentada, não um bug (ver CLAUDE.md).
     */
    public List<OsmRadar> findRadarsInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        String query = String.format(Locale.ROOT, """
                [out:json][timeout:8][bbox:%f,%f,%f,%f];
                (
                  node["highway"="speed_camera"];
                  node["highway"="red_light_camera"];
                  node["enforcement"="traffic_signals"];
                  node["man_made"="redlight_camera"];
                  node["enforcement_camera"="yes"];
                  relation["type"="enforcement"]["enforcement"="traffic_signals"];
                );
                out center;
                """, minLat, minLon, maxLat, maxLon);

        JsonNode response = executeQuery(query);

        List<OsmRadar> result = new ArrayList<>();
        for (JsonNode el : response.path("elements")) {
            double[] coords = extractCoordinates(el);
            if (coords == null) {
                continue;
            }
            boolean radarDeVelocidade = "speed_camera".equals(el.path("tags").path("highway").asText(null));
            RadarType tipo = radarDeVelocidade ? RadarType.VELOCIDADE : RadarType.AVANCO_SINAL;
            result.add(new OsmRadar(tipo, coords[0], coords[1]));
        }
        return result;
    }

    private JsonNode executeQuery(String overpassQlQuery) {
        return restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("data=" + URLEncoder.encode(overpassQlQuery, StandardCharsets.UTF_8))
                .retrieve()
                .body(JsonNode.class);
    }

    /** Retorna [lat, lon] ou null se o elemento não tiver coordenada válida. */
    private double[] extractCoordinates(JsonNode element) {
        double lat;
        double lon;
        if (element.has("center")) {
            lat = element.path("center").path("lat").asDouble();
            lon = element.path("center").path("lon").asDouble();
        } else {
            lat = element.path("lat").asDouble();
            lon = element.path("lon").asDouble();
        }
        if (lat == 0.0 && lon == 0.0) {
            return null;
        }
        return new double[] { lat, lon };
    }
}
