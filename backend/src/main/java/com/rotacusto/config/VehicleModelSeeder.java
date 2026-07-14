package com.rotacusto.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.repository.VehicleModelRepository;

/**
 * Carrega o catálogo de veículos a partir de data/vehicle-models.json.
 *
 * Consumo (consumoCidadeKmL/consumoEstradaKmL) vem das Tabelas PBE Veicular
 * oficiais do INMETRO (gov.br/inmetro) — **anos 2016 a 2020 e 2026** (~3.400
 * modelos/versões no total). Cada ano tem um "ano" próprio no catálogo, então
 * o mesmo modelo pode aparecer várias vezes com o consumo daquele ano
 * específico. Veículos 100% elétricos/plug-in foram deixados de fora — o
 * motor de custo é por preço de combustível (R$/litro), não por kWh.
 *
 * Faltam os anos 2021-2025: a extração desses PDFs específicos (via
 * tabula-java) tem colunas com posições inconsistentes entre páginas do
 * mesmo documento — um mapeamento fixo por ano captura só uma fração das
 * linhas reais (confirmado: contagem de "Combustão/Híbrido" no texto bruto
 * é 10-40x maior que o número de linhas parseadas com sucesso). Precisa de
 * uma extração com re-detecção de cabeçalho por página, não por documento
 * inteiro — não implementado ainda.
 *
 * Desgaste (custoDesgastePorKm) NÃO tem fonte oficial no Brasil — continua
 * sendo uma estimativa por categoria/segmento do próprio INMETRO (Sub
 * Compacto, Compacto, Médio, SUV, Picape etc.), documentada como placeholder
 * a refinar.
 */
@Component
public class VehicleModelSeeder implements CommandLineRunner {

    private final VehicleModelRepository repository;
    private final ObjectMapper objectMapper;

    public VehicleModelSeeder(VehicleModelRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            return;
        }
        try (var input = new ClassPathResource("data/vehicle-models.json").getInputStream()) {
            List<VehicleModel> models = objectMapper.readValue(input, new com.fasterxml.jackson.core.type.TypeReference<List<VehicleModel>>() {
            });
            repository.saveAll(models);
        }
    }
}
