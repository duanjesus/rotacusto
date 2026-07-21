package com.rotacusto.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmRadar;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;

/**
 * Câmeras de velocidade (radares fixos) reais ao longo da rota (OpenStreetMap via
 * Overpass, tag {@code highway=speed_camera}) — Fase 12. Ao contrário de
 * {@link RoadAlertService}/{@link TrafficReportService}, é infraestrutura permanente:
 * sem relato de usuário, sem expiração, sem voto — só consulta ao vivo.
 */
@Service
public class RadarService {

    private static final Logger log = LoggerFactory.getLogger(RadarService.class);
    private static final double BBOX_PADDING_DEGREES = 0.05; // ~5km

    private final OverpassClient overpassClient;
    private final double detectionRadiusKm;

    public RadarService(
            OverpassClient overpassClient,
            @Value("${rotacusto.radars.detection-radius-km}") double detectionRadiusKm) {
        this.overpassClient = overpassClient;
        this.detectionRadiusKm = detectionRadiusKm;
    }

    public List<OsmRadar> findCamerasNearRoute(List<Coordinates> geometriaRota) {
        try {
            double[] bbox = BoundingBoxCalculator.compute(geometriaRota, BBOX_PADDING_DEGREES);
            List<OsmRadar> candidates = overpassClient.findRadarsInBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3]);
            return candidates.stream()
                    .filter(c -> isNearRoute(c, geometriaRota))
                    .toList();
        } catch (Exception e) {
            log.warn("Falha ao consultar radares via OpenStreetMap (Overpass)", e);
            return List.of();
        }
    }

    private boolean isNearRoute(OsmRadar radar, List<Coordinates> geometriaRota) {
        Coordinates ponto = new Coordinates(radar.lat(), radar.lon());
        return geometriaRota.stream().anyMatch(p -> HaversineDistance.km(p, ponto) <= detectionRadiusKm);
    }
}
