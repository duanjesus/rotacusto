package com.rotacusto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;
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
        assertEquals(7802, vehicleModelRepository.count());
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

        var todos = vehicleModelRepository.findAll();
        for (TipoCombustivel tipo : TipoCombustivel.values()) {
            long comEsseTipo = todos.stream().filter(v -> v.getTipoCombustivel() == tipo).count();
            assertTrue(comEsseTipo > 0, "catálogo deveria ter pelo menos um veículo " + tipo);
        }
        assertTrue(todos.stream()
                .filter(v -> v.getTipoCombustivel() == TipoCombustivel.ELETRICO)
                .allMatch(v -> v.getConsumoKmPorKWh() != null),
                "todo veículo elétrico deveria ter consumoKmPorKWh preenchido");
        assertTrue(todos.stream()
                .filter(v -> v.getTipoCombustivel() != TipoCombustivel.ELETRICO)
                .allMatch(v -> v.getConsumoCidadeKmL() != null && v.getConsumoEstradaKmL() != null),
                "todo veículo a combustão deveria ter consumo em km/L preenchido");

        long motos = todos.stream().filter(v -> v.getTipo() == VehicleType.MOTO).count();
        assertTrue(motos > 0, "catálogo deveria ter pelo menos uma moto (Fase 3)");
        assertTrue(todos.stream()
                .filter(v -> v.getTipo() == VehicleType.MOTO)
                .allMatch(v -> v.getCilindradaCC() != null && v.getCilindradaCC() > 0),
                "toda moto deveria ter cilindrada preenchida");

        // Vans/caminhões leves/micro-ônibus vieram de reclassificar entradas já
        // existentes (extraídas do PBE/INMETRO como CARRO) por nome de modelo
        // conhecido — não são dados novos, então mantêm consumo/desgaste reais.
        long vans = todos.stream().filter(v -> v.getTipo() == VehicleType.VAN).count();
        assertTrue(vans > 0, "catálogo deveria ter pelo menos uma van (Fase 3)");
        long caminhoes = todos.stream().filter(v -> v.getTipo() == VehicleType.CAMINHAO).count();
        assertTrue(caminhoes > 0, "catálogo deveria ter pelo menos um caminhão (Fase 3)");
        long onibus = todos.stream().filter(v -> v.getTipo() == VehicleType.ONIBUS).count();
        assertTrue(onibus > 0, "catálogo deveria ter pelo menos um ônibus (Fase 3)");
    }

    @Test
    void tollPlazaSeedDataIsLoadedOnStartup() {
        assertEquals(6, tollPlazaRepository.count());
        assertTrue(tollPlazaRepository.findAll().stream()
                .anyMatch(p -> p.getCobraApenasIndo() != null),
                "deveria ter pelo menos uma praça de sentido único cadastrada");
    }
}
