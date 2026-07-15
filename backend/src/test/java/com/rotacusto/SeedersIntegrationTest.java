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
        assertEquals(9003, vehicleModelRepository.count());
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

        // Caminhão/ônibus PESADOS (curados por PBT, sem fonte PBE) coexistem com
        // os leves reetiquetados acima (que mantêm consumo real, sem pbtKg).
        assertTrue(todos.stream()
                .filter(v -> v.getTipo() == VehicleType.CAMINHAO)
                .anyMatch(v -> v.getPbtKg() != null && v.getPbtKg() > 0),
                "catálogo deveria ter pelo menos um caminhão pesado com pbtKg preenchido");
        assertTrue(todos.stream()
                .filter(v -> v.getTipo() == VehicleType.ONIBUS)
                .anyMatch(v -> v.getPbtKg() != null && v.getPbtKg() > 0),
                "catálogo deveria ter pelo menos um ônibus pesado com pbtKg preenchido");
        long modelosOnibusPesado = todos.stream()
                .filter(v -> v.getTipo() == VehicleType.ONIBUS && v.getPbtKg() != null)
                .map(v -> v.getMarca() + "|" + v.getModelo())
                .distinct()
                .count();
        assertEquals(9, modelosOnibusPesado, "catálogo deveria ter 9 modelos distintos de ônibus pesado");

        // Moto e caminhão/ônibus pesados (só estimativa, sem fonte PBE por
        // ano) foram replicados em 2016-2026 pra ter a mesma faixa de anos
        // que carro — pedido explícito do usuário, mesmo sem dado real
        // por ano-modelo.
        long anosDaMoto = todos.stream()
                .filter(v -> v.getTipo() == VehicleType.MOTO)
                .map(v -> v.getAno())
                .distinct()
                .count();
        assertEquals(11, anosDaMoto, "moto deveria cobrir os 11 anos de 2016 a 2026");
        long anosDoCaminhaoPesado = todos.stream()
                .filter(v -> v.getTipo() == VehicleType.CAMINHAO && v.getPbtKg() != null)
                .map(v -> v.getAno())
                .distinct()
                .count();
        assertEquals(11, anosDoCaminhaoPesado, "caminhão pesado deveria cobrir os 11 anos de 2016 a 2026");
        long anosDoOnibusPesado = todos.stream()
                .filter(v -> v.getTipo() == VehicleType.ONIBUS && v.getPbtKg() != null)
                .map(v -> v.getAno())
                .distinct()
                .count();
        assertEquals(11, anosDoOnibusPesado, "ônibus pesado deveria cobrir os 11 anos de 2016 a 2026");

        // Híbrido plug-in: cada modelo tem uma linha ELETRICO (autonomia da
        // bateria) e uma linha a combustão pareadas no mesmo ano — mesmo
        // padrão de carro flex, corrigindo o número "equivalente" enganoso
        // que a extração original do PBE capturava pra PHEV.
        var xc90EletricoPorAno = todos.stream()
                .filter(v -> v.getMarca().equals("Volvo") && v.getModelo().equals("XC90 Recharge T8")
                        && v.getTipoCombustivel() == TipoCombustivel.ELETRICO)
                .map(v -> v.getAno())
                .collect(java.util.stream.Collectors.toSet());
        var xc90GasolinaPorAno = todos.stream()
                .filter(v -> v.getMarca().equals("Volvo") && v.getModelo().equals("XC90 Recharge T8")
                        && v.getTipoCombustivel() == TipoCombustivel.GASOLINA)
                .map(v -> v.getAno())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(!xc90EletricoPorAno.isEmpty(), "Volvo XC90 Recharge T8 deveria ter linha ELETRICO");
        assertEquals(xc90EletricoPorAno, xc90GasolinaPorAno,
                "Volvo XC90 Recharge T8 deveria ter os mesmos anos pareados entre ELETRICO e GASOLINA");
    }

    @Test
    void tollPlazaSeedDataIsLoadedOnStartup() {
        assertEquals(6, tollPlazaRepository.count());
        assertTrue(tollPlazaRepository.findAll().stream()
                .anyMatch(p -> p.getCobraApenasIndo() != null),
                "deveria ter pelo menos uma praça de sentido único cadastrada");
    }
}
