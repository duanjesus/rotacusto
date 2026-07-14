package com.rotacusto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.rotacusto.entity.enums.TipoEnergia;
import com.rotacusto.repository.TollPlazaRepository;
import com.rotacusto.repository.VehicleModelRepository;

/**
 * Sobe o contexto Spring completo (com H2 em memória) e confirma que os
 * seeders de catálogo de veículos e praças de pedágio carregam os dados.
 */
@SpringBootTest
class SeedersIntegrationTest {

    @Autowired
    private VehicleModelRepository vehicleModelRepository;

    @Autowired
    private TollPlazaRepository tollPlazaRepository;

    @Test
    void vehicleModelCatalogIsSeededOnStartup() {
        assertEquals(5808, vehicleModelRepository.count());
        long marcasDistintas = vehicleModelRepository.findAll().stream()
                .map(v -> v.getMarca())
                .distinct()
                .count();
        assertTrue(marcasDistintas >= 25, "catálogo deveria cobrir pelo menos 25 marcas distintas");

        long anosDistintos = vehicleModelRepository.findAll().stream()
                .map(v -> v.getAno())
                .distinct()
                .count();
        assertTrue(anosDistintos >= 5, "catálogo deveria cobrir vários anos-modelo, não só o mais recente");

        long eletricos = vehicleModelRepository.findAll().stream()
                .filter(v -> v.getTipoEnergia() == TipoEnergia.ELETRICO)
                .count();
        assertTrue(eletricos > 0, "catálogo deveria ter pelo menos um veículo elétrico");
        assertTrue(vehicleModelRepository.findAll().stream()
                .filter(v -> v.getTipoEnergia() == TipoEnergia.ELETRICO)
                .allMatch(v -> v.getConsumoKmPorKWh() != null),
                "todo veículo elétrico deveria ter consumoKmPorKWh preenchido");
    }

    @Test
    void tollPlazaSeedDataIsLoadedOnStartup() {
        assertEquals(6, tollPlazaRepository.count());
        assertTrue(tollPlazaRepository.findAll().stream()
                .anyMatch(p -> p.getCobraApenasIndo() != null),
                "deveria ter pelo menos uma praça de sentido único cadastrada");
    }
}
