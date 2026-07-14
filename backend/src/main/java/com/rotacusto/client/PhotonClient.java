package com.rotacusto.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotacusto.domain.AddressSuggestion;

/**
 * Client do Photon (photon.komoot.io), um geocoder sobre dados OpenStreetMap
 * feito para autocomplete — ao contrário do Nominatim, aceita busca por
 * prefixo (palavra incompleta enquanto o usuário digita).
 *
 * Sem viés geográfico artificial: testamos com um ponto fixo no centro do
 * Brasil (Brasília) e ele DISTORCIA o ranking (ex: "Rio das Ost" não trazia
 * "Rio das Ostras" no topo). Em vez disso, pedimos mais resultados do que o
 * necessário e filtramos por countrycode=BR depois — o app é Brasil-only.
 */
@Component
public class PhotonClient {

    private static final int OVERFETCH_FACTOR = 4;

    private final RestClient restClient;

    public PhotonClient(@Value("${rotacusto.geocoding.photon-base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<AddressSuggestion> search(String query, int limit) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api")
                        .queryParam("q", query)
                        .queryParam("limit", limit * OVERFETCH_FACTOR)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<AddressSuggestion> results = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (JsonNode feature : response.path("features")) {
            JsonNode props = feature.path("properties");
            if (!"BR".equals(props.path("countrycode").asText(null))) {
                continue;
            }
            JsonNode coords = feature.path("geometry").path("coordinates"); // GeoJSON: [lon, lat]
            if (coords.size() < 2) {
                continue;
            }
            double lat = coords.get(1).asDouble();
            double lon = coords.get(0).asDouble();
            String displayName = buildDisplayName(props);
            if (!seen.add(displayName + "|" + lat + "|" + lon)) {
                continue; // mesmo lugar já retornado (Photon às vezes duplica)
            }
            results.add(new AddressSuggestion(displayName, lat, lon));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private String buildDisplayName(JsonNode props) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, props, "name");
        addIfPresent(parts, props, "street");
        addIfPresent(parts, props, "city");
        addIfPresent(parts, props, "state");
        addIfPresent(parts, props, "country");
        return String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, JsonNode props, String field) {
        String value = props.path(field).asText(null);
        if (value != null && !parts.contains(value)) {
            parts.add(value);
        }
    }
}
