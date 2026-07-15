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
 * ATENÇÃO: as 4 praças do corredor BR-101 RJ→ES têm coordenadas de centro
 * aproximado do município (não o ponto exato da praça na rodovia) e tarifa
 * ainda estimada — dataset a refinar com dados oficiais da concessionária/
 * ANTT antes de uso real. Por isso o raio de detecção
 * (rotacusto.tolls.detection-radius-km) é generoso.
 *
 * As duas praças de Itaboraí (RJ-116, sentido único) e da Ponte Rio-Niterói
 * (BR-101, sentido único) são exceção: coordenadas já confirmadas ao vivo
 * pelo usuário dirigindo a rota real (Fase 2), e a tarifa foi atualizada
 * com valor REAL confirmado (não mais estimativa) — Rota 116 (Itaboraí,
 * km 1,9): R$9,60/carro (tarifaPorEixo=4,80 pra um carro de 2 eixos),
 * **moto isenta** (Lei estadual de isenção, confirmado — tarifaMoto=0,0,
 * não confundir com dado ausente); Ecovias Ponte/EcoRodovias (Ponte
 * Rio-Niterói, BR-101/RJ km 322+100): R$6,60/carro, R$3,30/moto
 * (reajuste de 18/03/2026). Fontes: sites das próprias concessionárias e
 * imprensa especializada do RJ, pesquisado especificamente porque o
 * usuário perguntou por esses dois (só tinham estimativa até então).
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
