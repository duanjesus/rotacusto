package com.rotacusto.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.rotacusto.client.NominatimClient;
import com.rotacusto.domain.Coordinates;

@Service
public class GeocodingService {

    private static final Pattern COORDS_PATTERN = Pattern.compile(
            "^\\s*(-?\\d+(\\.\\d+)?)\\s*,\\s*(-?\\d+(\\.\\d+)?)\\s*$");

    private final NominatimClient client;

    public GeocodingService(NominatimClient client) {
        this.client = client;
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
}
