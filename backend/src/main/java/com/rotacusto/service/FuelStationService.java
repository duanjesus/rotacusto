package com.rotacusto.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;

/**
 * Postos de combustível reais ao longo da rota (OpenStreetMap via Overpass).
 * O preço usado no cálculo de combustível continua sendo uma média informada
 * pelo usuário — não existe fonte gratuita de preço por posto em tempo real
 * no Brasil, então aqui só localizamos os postos e sugerimos onde parar.
 */
@Service
public class FuelStationService {

    private static final Logger log = LoggerFactory.getLogger(FuelStationService.class);
    private static final double BBOX_PADDING_DEGREES = 0.05; // ~5km

    private final OverpassClient overpassClient;
    private final double detectionRadiusKm;

    public FuelStationService(
            OverpassClient overpassClient,
            @Value("${rotacusto.fuel-stations.detection-radius-km}") double detectionRadiusKm) {
        this.overpassClient = overpassClient;
        this.detectionRadiusKm = detectionRadiusKm;
    }

    public List<OsmFuelStation> findStationsNearRoute(List<Coordinates> geometriaRota) {
        try {
            double[] bbox = BoundingBoxCalculator.compute(geometriaRota, BBOX_PADDING_DEGREES);
            List<OsmFuelStation> candidates = overpassClient.findFuelStationsInBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3]);
            return candidates.stream()
                    .filter(c -> isNearRoute(c, geometriaRota))
                    .toList();
        } catch (Exception e) {
            log.warn("Falha ao consultar postos de combustível via OpenStreetMap (Overpass)", e);
            return List.of();
        }
    }

    /**
     * Sugere o posto mais próximo do meio do trajeto (por distância
     * acumulada, não pelo índice do ponto). É uma heurística simples de
     * "parada no meio do caminho" — ainda não considera a autonomia real do
     * tanque, já que o catálogo de veículos não tem essa capacidade.
     */
    public Optional<OsmFuelStation> suggestStop(List<OsmFuelStation> stations, List<Coordinates> geometriaRota) {
        if (stations.isEmpty() || geometriaRota.isEmpty()) {
            return Optional.empty();
        }
        Coordinates midpoint = findRouteMidpoint(geometriaRota);
        return stations.stream()
                .min(Comparator.comparingDouble(s -> HaversineDistance.km(midpoint, new Coordinates(s.lat(), s.lon()))));
    }

    private boolean isNearRoute(OsmFuelStation station, List<Coordinates> geometriaRota) {
        Coordinates ponto = new Coordinates(station.lat(), station.lon());
        return geometriaRota.stream().anyMatch(p -> HaversineDistance.km(p, ponto) <= detectionRadiusKm);
    }

    private Coordinates findRouteMidpoint(List<Coordinates> route) {
        double totalKm = 0;
        for (int i = 1; i < route.size(); i++) {
            totalKm += HaversineDistance.km(route.get(i - 1), route.get(i));
        }
        double halfKm = totalKm / 2;
        double accumulated = 0;
        for (int i = 1; i < route.size(); i++) {
            accumulated += HaversineDistance.km(route.get(i - 1), route.get(i));
            if (accumulated >= halfKm) {
                return route.get(i);
            }
        }
        return route.get(route.size() / 2);
    }
}
