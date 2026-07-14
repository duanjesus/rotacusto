package com.rotacusto.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmTollBooth;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

/**
 * Detecta as praças de pedágio cruzadas pela rota. O OpenStreetMap (via
 * Overpass API) é a fonte de verdade sobre QUAIS pedágios existem — cobertura
 * nacional, localização real e precisa. O dataset curado (poucas praças,
 * coordenadas aproximadas de município — ver TollPlazaSeeder) nunca gera uma
 * entrada própria; ele só ENRIQUECE a tarifa/nome de um pedágio já achado
 * pelo OSM quando as coordenadas batem, evitando contar a mesma praça física
 * duas vezes. Sem um match curado, usa-se uma tarifa média nacional.
 * Se o Overpass falhar, cai para detecção só pelo dataset curado.
 */
@Service
public class TollService {

    private static final Logger log = LoggerFactory.getLogger(TollService.class);
    private static final double BBOX_PADDING_DEGREES = 0.05; // ~5km
    /** O OSM mapeia cada faixa/cabine de uma praça como um nó separado; pontos
     * dentro desse raio são tratados como a mesma praça física. */
    private static final double OSM_CLUSTER_RADIUS_KM = 0.3;

    private final TollPlazaRepository repository;
    private final OverpassClient overpassClient;
    private final double curatedDetectionRadiusKm;
    private final double osmDetectionRadiusKm;
    private final double defaultTarifaPorEixo;
    private final double defaultTarifaMoto;

    public TollService(
            TollPlazaRepository repository,
            OverpassClient overpassClient,
            @Value("${rotacusto.tolls.curated-detection-radius-km}") double curatedDetectionRadiusKm,
            @Value("${rotacusto.tolls.osm-detection-radius-km}") double osmDetectionRadiusKm,
            @Value("${rotacusto.tolls.default-tarifa-por-eixo}") double defaultTarifaPorEixo,
            @Value("${rotacusto.tolls.default-tarifa-moto}") double defaultTarifaMoto) {
        this.repository = repository;
        this.overpassClient = overpassClient;
        this.curatedDetectionRadiusKm = curatedDetectionRadiusKm;
        this.osmDetectionRadiusKm = osmDetectionRadiusKm;
        this.defaultTarifaPorEixo = defaultTarifaPorEixo;
        this.defaultTarifaMoto = defaultTarifaMoto;
    }

    public List<TollPlaza> findCrossedPlazas(List<Coordinates> geometriaRota) {
        List<TollPlaza> curated = repository.findAll();

        List<OsmTollBooth> osmCandidates;
        try {
            double[] bbox = BoundingBoxCalculator.compute(geometriaRota, BBOX_PADDING_DEGREES);
            osmCandidates = overpassClient.findTollBoothsInBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3]);
        } catch (Exception e) {
            log.warn("Falha ao consultar pedágios via OpenStreetMap (Overpass); caindo para o dataset curado", e);
            return curated.stream()
                    .filter(p -> isCrossedBy(p.getLat(), p.getLng(), geometriaRota, curatedDetectionRadiusKm))
                    .toList();
        }

        List<OsmTollBooth> nearRoute = osmCandidates.stream()
                .filter(c -> isCrossedBy(c.lat(), c.lon(), geometriaRota, osmDetectionRadiusKm))
                .toList();

        return clusterNearbyBooths(nearRoute).stream()
                .map(osm -> enrichWithCuratedTariff(osm, curated))
                .toList();
    }

    private TollPlaza enrichWithCuratedTariff(OsmTollBooth osm, List<TollPlaza> curated) {
        Optional<TollPlaza> match = curated.stream()
                .filter(c -> HaversineDistance.km(new Coordinates(c.getLat(), c.getLng()), new Coordinates(osm.lat(), osm.lon())) <= curatedDetectionRadiusKm)
                .findFirst();

        if (match.isEmpty()) {
            return toTollPlaza(osm);
        }

        TollPlaza curatedPlaza = match.get();
        TollPlaza p = new TollPlaza();
        p.setNome(curatedPlaza.getNome());
        p.setRodovia(curatedPlaza.getRodovia());
        p.setConcessionaria(curatedPlaza.getConcessionaria());
        p.setLat(osm.lat()); // usa a coordenada real e precisa do OSM
        p.setLng(osm.lon());
        p.setTarifaPorEixo(curatedPlaza.getTarifaPorEixo());
        p.setTarifaMoto(curatedPlaza.getTarifaMoto());
        return p;
    }

    /** Colapsa nós do OSM muito próximos entre si (mesma praça, faixas diferentes) num só representante. */
    private List<OsmTollBooth> clusterNearbyBooths(List<OsmTollBooth> booths) {
        List<OsmTollBooth> representatives = new ArrayList<>();
        for (OsmTollBooth candidate : booths) {
            boolean isDuplicate = representatives.stream().anyMatch(rep -> HaversineDistance.km(
                    new Coordinates(rep.lat(), rep.lon()), new Coordinates(candidate.lat(), candidate.lon())) <= OSM_CLUSTER_RADIUS_KM);
            if (!isDuplicate) {
                representatives.add(candidate);
            }
        }
        return representatives;
    }

    private TollPlaza toTollPlaza(OsmTollBooth osm) {
        TollPlaza p = new TollPlaza();
        p.setNome(osm.nome());
        p.setRodovia(osm.rodovia());
        p.setConcessionaria("Não identificada (OpenStreetMap)");
        p.setLat(osm.lat());
        p.setLng(osm.lon());
        p.setTarifaPorEixo(defaultTarifaPorEixo);
        p.setTarifaMoto(defaultTarifaMoto);
        return p;
    }

    private boolean isCrossedBy(double lat, double lon, List<Coordinates> geometriaRota, double radiusKm) {
        Coordinates ponto = new Coordinates(lat, lon);
        return geometriaRota.stream().anyMatch(p -> HaversineDistance.km(p, ponto) <= radiusKm);
    }
}
