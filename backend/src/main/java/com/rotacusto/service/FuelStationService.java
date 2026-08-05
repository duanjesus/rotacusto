package com.rotacusto.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.client.NominatimClient;
import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.PricedFuelStation;
import com.rotacusto.domain.ReverseGeocodeResult;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.enums.TipoCombustivel;

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
    private final NominatimClient nominatimClient;
    private final FuelPriceService fuelPriceService;
    private final double detectionRadiusKm;
    private final int melhorPrecoCandidatosK;
    private final long reverseGeocodeDelayMs;

    public FuelStationService(
            OverpassClient overpassClient,
            NominatimClient nominatimClient,
            FuelPriceService fuelPriceService,
            @Value("${rotacusto.fuel-stations.detection-radius-km}") double detectionRadiusKm,
            @Value("${rotacusto.fuel-stations.melhor-preco-candidatos-k}") int melhorPrecoCandidatosK,
            @Value("${rotacusto.fuel-stations.reverse-geocode-delay-ms}") long reverseGeocodeDelayMs) {
        this.overpassClient = overpassClient;
        this.nominatimClient = nominatimClient;
        this.fuelPriceService = fuelPriceService;
        this.detectionRadiusKm = detectionRadiusKm;
        this.melhorPrecoCandidatosK = melhorPrecoCandidatosK;
        this.reverseGeocodeDelayMs = reverseGeocodeDelayMs;
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

    /**
     * Sugere o posto com o MELHOR PREÇO entre um pequeno grupo de candidatos
     * próximos ao meio da rota (Fase 15) — não o posto mais barato de TODA a
     * rota. Reverse-geocodificar todos os postos candidatos (uma rota urbana
     * pode ter 100+) não é viável contra o Nominatim público (~1 req/s, ver
     * {@link com.rotacusto.client.NominatimClient}); em vez disso, só os
     * {@code melhorPrecoCandidatosK} mais próximos do meio geométrico da rota
     * (mesmo ponto usado por {@link #suggestStop}) são reverse-geocodificados,
     * em sequência (nunca em paralelo — respeitar o rate limit é o objetivo),
     * pra descobrir o município de cada um e cruzar com
     * {@link FuelPriceService#buscarPrecoPorMunicipio}.
     *
     * <p>Falhas são toleradas por candidato (Nominatim fora do ar, timeout,
     * município sem preço na tabela) sem abortar o laço. Se NENHUM dos K
     * candidatos foi precificado, degrada pra {@link #suggestStop} (o
     * comportamento de sempre, nearest-to-midpoint) — nunca retorna vazio só
     * porque o reverse geocoding falhou, desde que existam postos candidatos.
     *
     * <p>Não se aplica a veículos {@link TipoCombustivel#ELETRICO} — não há
     * conceito de "melhor preço de combustível" pra carro elétrico (o
     * chamador já filtra isso antes, ver {@code TripEstimationService}).
     */
    public Optional<PricedFuelStation> suggestBestPricedStop(List<OsmFuelStation> stations,
            List<Coordinates> geometriaRota, TipoCombustivel tipoCombustivel) {
        if (stations.isEmpty() || geometriaRota.isEmpty()) {
            return Optional.empty();
        }
        Coordinates midpoint = findRouteMidpoint(geometriaRota);
        List<OsmFuelStation> candidatos = stations.stream()
                .sorted(Comparator.comparingDouble(s -> HaversineDistance.km(midpoint, new Coordinates(s.lat(), s.lon()))))
                .limit(melhorPrecoCandidatosK)
                .toList();

        PricedFuelStation melhor = null;
        for (OsmFuelStation candidato : candidatos) {
            PricedFuelStation precificado = precificar(candidato, tipoCombustivel);
            if (precificado.precoMedio() != null
                    && (melhor == null || precificado.precoMedio() < melhor.precoMedio())) {
                melhor = precificado;
            }
        }

        if (melhor != null) {
            return Optional.of(melhor);
        }
        // Nenhum candidato precificado (Nominatim indisponível, ou nenhum
        // município bateu na tabela ANP) — degrada pro comportamento de sempre.
        return suggestStop(stations, geometriaRota).map(s -> new PricedFuelStation(s, null, null));
    }

    private PricedFuelStation precificar(OsmFuelStation candidato, TipoCombustivel tipoCombustivel) {
        try {
            Optional<ReverseGeocodeResult> localizacao = nominatimClient.reverseGeocode(candidato.lat(), candidato.lon());
            if (localizacao.isEmpty()) {
                return new PricedFuelStation(candidato, null, null);
            }
            Optional<Double> preco = fuelPriceService.buscarPrecoPorMunicipio(
                    localizacao.get().municipio(), localizacao.get().uf(), tipoCombustivel);
            return new PricedFuelStation(candidato, preco.orElse(null), localizacao.get().municipio());
        } catch (Exception e) {
            log.warn("Falha ao precificar posto candidato via reverse geocoding (Nominatim)", e);
            return new PricedFuelStation(candidato, null, null);
        } finally {
            // Nunca rajar mais rápido que a etiqueta do Nominatim, mesmo em falha.
            try {
                Thread.sleep(reverseGeocodeDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
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
