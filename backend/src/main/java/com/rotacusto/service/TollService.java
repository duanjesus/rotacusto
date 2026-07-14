package com.rotacusto.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.geo.HaversineDistance;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

@Service
public class TollService {

    private final TollPlazaRepository repository;
    private final double detectionRadiusKm;

    public TollService(
            TollPlazaRepository repository,
            @Value("${rotacusto.tolls.detection-radius-km}") double detectionRadiusKm) {
        this.repository = repository;
        this.detectionRadiusKm = detectionRadiusKm;
    }

    /**
     * Retorna as praças cujo ponto fica a até {@code detectionRadiusKm} de algum
     * ponto da geometria da rota.
     */
    public List<TollPlaza> findCrossedPlazas(List<Coordinates> geometriaRota) {
        return repository.findAll().stream()
                .filter(praca -> isCrossedBy(praca, geometriaRota))
                .toList();
    }

    private boolean isCrossedBy(TollPlaza praca, List<Coordinates> geometriaRota) {
        Coordinates plazaCoord = new Coordinates(praca.getLat(), praca.getLng());
        return geometriaRota.stream()
                .anyMatch(ponto -> HaversineDistance.km(ponto, plazaCoord) <= detectionRadiusKm);
    }
}
