package com.rotacusto.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.rotacusto.client.NominatimClient;
import com.rotacusto.client.PhotonClient;
import com.rotacusto.domain.AddressSuggestion;
import com.rotacusto.domain.Coordinates;

@Service
public class GeocodingService {

    private static final Pattern COORDS_PATTERN = Pattern.compile(
            "^\\s*(-?\\d+(\\.\\d+)?)\\s*,\\s*(-?\\d+(\\.\\d+)?)\\s*$");
    private static final int SUGGESTION_LIMIT = 5;
    private static final int MIN_QUERY_LENGTH = 3;

    private final NominatimClient client;
    private final PhotonClient photonClient;

    public GeocodingService(NominatimClient client, PhotonClient photonClient) {
        this.client = client;
        this.photonClient = photonClient;
    }

    /**
     * Aceita um endereço em texto livre ou coordenadas já prontas ("lat,lon").
     */
    public Coordinates resolve(String origemOuDestino) {
        Matcher matcher = COORDS_PATTERN.matcher(origemOuDestino);
        if (matcher.matches()) {
            return new Coordinates(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(3)));
        }
        return client.geocode(origemOuDestino);
    }

    /**
     * Sugestões de endereço para autocomplete (aceita palavra incompleta).
     * Usa o Photon, não o Nominatim — o Nominatim só casa tokens completos e
     * não serve para "digitando". Consultas curtas demais são ignoradas.
     */
    public List<AddressSuggestion> suggest(String query) {
        if (!StringUtils.hasText(query) || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return photonClient.search(query, SUGGESTION_LIMIT);
    }
}
