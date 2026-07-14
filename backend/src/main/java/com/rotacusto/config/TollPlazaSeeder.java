package com.rotacusto.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

/**
 * Carrega o dataset-semente de praças de pedágio do corredor BR-101 RJ→ES a
 * partir de data/tollplazas-br101.json.
 *
 * ATENÇÃO: coordenadas são centros aproximados dos municípios (não o ponto
 * exato da praça na rodovia) e tarifas são valores demo estimados — dataset
 * a refinar com dados oficiais da concessionária/ANTT antes de uso real.
 * Por isso o raio de detecção (rotacusto.tolls.detection-radius-km) é generoso.
 */
@Component
public class TollPlazaSeeder implements CommandLineRunner {

    private final TollPlazaRepository repository;
    private final ObjectMapper objectMapper;

    public TollPlazaSeeder(TollPlazaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            return;
        }
        try (var input = new ClassPathResource("data/tollplazas-br101.json").getInputStream()) {
            List<TollPlaza> plazas = objectMapper.readValue(input, new TypeReference<List<TollPlaza>>() {
            });
            repository.saveAll(plazas);
        }
    }
}
