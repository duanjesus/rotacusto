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
import com.rotacusto.domain.geo.Bearing;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

/**
 * Detecta as praças de pedágio cruzadas pela rota. O OpenStreetMap (via
 * Overpass API) é a fonte de verdade sobre QUAIS pedágios existem — cobertura
 * nacional, localização real e precisa. O dataset curado (poucas praças,
 * coordenadas aproximadas de município — ver TollPlazaSeeder) nunca gera uma
 * entrada própria; ele só ENRIQUECE a tarifa/nome/direção de um pedágio já
 * achado pelo OSM quando as coordenadas batem, evitando contar a mesma praça
 * física duas vezes. Sem um match curado, usa-se uma tarifa média nacional.
 * Se o Overpass falhar, cai para detecção só pelo dataset curado.
 *
 * O raio de detecção pelo OSM (osm-detection-radius-km) é apertado de
 * propósito: muitas praças no Brasil cobram só um sentido — a cabine física
 * só existe numa das pistas de uma rodovia dividida. Um raio largo demais
 * "atravessa" o canteiro central e conta também o pedágio da pista oposta.
 *
 * Praças de sentido único de verdade (a mesma cabine física, mas só cobra
 * indo ou só cobra voltando em relação a um ponto de referência) não dá pra
 * distinguir só por proximidade — por isso o dataset curado pode marcar
 * refLat/refLng/cobraApenasIndo, e comparamos o rumo (bearing) da viagem
 * naquele trecho contra o rumo até a referência.
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
                    .filter(p -> passesDirectionConstraint(p, geometriaRota))
                    .map(this::withDefaultTariffIfMissing)
                    .toList();
        }

        List<OsmTollBooth> nearRoute = osmCandidates.stream()
                .filter(c -> isCrossedBy(c.lat(), c.lon(), geometriaRota, osmDetectionRadiusKm))
                .toList();

        return clusterNearbyBooths(nearRoute).stream()
                .map(osm -> enrichWithCuratedTariff(osm, curated))
                .filter(p -> passesDirectionConstraint(p, geometriaRota))
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
        // Nem toda praça curada tem tarifa confirmada (dataset nacional da ANTT dá
        // localização/sentido reais pra todas as praças federais, mas tarifa só foi
        // confirmada por concessão quando não varia de praça pra praça — ver
        // TollPlazaSeeder). Sem isso, tarifaPorEixo nulo quebraria TollCostCalculator
        // (NPE no unboxing); cai pro padrão nacional em vez de propagar null, mas
        // mantém o nome/concessionária reais (melhor que "não identificada").
        p.setTarifaPorEixo(curatedPlaza.getTarifaPorEixo() != null ? curatedPlaza.getTarifaPorEixo() : defaultTarifaPorEixo);
        p.setTarifaMoto(curatedPlaza.getTarifaMoto() != null ? curatedPlaza.getTarifaMoto() : defaultTarifaMoto);
        p.setRefLat(curatedPlaza.getRefLat());
        p.setRefLng(curatedPlaza.getRefLng());
        p.setCobraApenasIndo(curatedPlaza.getCobraApenasIndo());
        return p;
    }

    /** Mesma regra de fallback de {@link #enrichWithCuratedTariff}, usada no caminho em
     * que o Overpass falhou e as próprias coordenadas curadas são usadas direto. */
    private TollPlaza withDefaultTariffIfMissing(TollPlaza curatedPlaza) {
        if (curatedPlaza.getTarifaPorEixo() != null) {
            return curatedPlaza;
        }
        TollPlaza p = new TollPlaza();
        p.setNome(curatedPlaza.getNome());
        p.setRodovia(curatedPlaza.getRodovia());
        p.setConcessionaria(curatedPlaza.getConcessionaria());
        p.setLat(curatedPlaza.getLat());
        p.setLng(curatedPlaza.getLng());
        p.setTarifaPorEixo(defaultTarifaPorEixo);
        p.setTarifaMoto(curatedPlaza.getTarifaMoto() != null ? curatedPlaza.getTarifaMoto() : defaultTarifaMoto);
        p.setRefLat(curatedPlaza.getRefLat());
        p.setRefLng(curatedPlaza.getRefLng());
        p.setCobraApenasIndo(curatedPlaza.getCobraApenasIndo());
        return p;
    }

    /**
     * Praças sem restrição de sentido (refLat/refLng nulos) sempre passam.
     * Com restrição: compara o rumo da viagem no trecho mais próximo da
     * praça contra o rumo da praça até o ponto de referência. Diferença
     * menor que 90° = indo em direção à referência; maior = se afastando.
     */
    private boolean passesDirectionConstraint(TollPlaza plaza, List<Coordinates> geometriaRota) {
        if (plaza.getRefLat() == null || plaza.getRefLng() == null || plaza.getCobraApenasIndo() == null) {
            return true;
        }
        if (geometriaRota.size() < 2) {
            return true;
        }

        Coordinates plazaPoint = new Coordinates(plaza.getLat(), plaza.getLng());
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < geometriaRota.size(); i++) {
            double d = HaversineDistance.km(geometriaRota.get(i), plazaPoint);
            if (d < nearestDistance) {
                nearestDistance = d;
                nearestIndex = i;
            }
        }

        Coordinates before = geometriaRota.get(Math.max(0, nearestIndex - 1));
        Coordinates after = geometriaRota.get(Math.min(geometriaRota.size() - 1, nearestIndex + 1));
        if (before.equals(after)) {
            return true; // não dá pra determinar o rumo (início/fim da rota)
        }

        double rumoViagem = Bearing.degrees(before, after);
        double rumoAteReferencia = Bearing.degrees(plazaPoint, new Coordinates(plaza.getRefLat(), plaza.getRefLng()));
        boolean indoEmDirecaoAReferencia = Bearing.angularDifference(rumoViagem, rumoAteReferencia) < 90.0;

        return plaza.getCobraApenasIndo() == indoEmDirecaoAReferencia;
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
