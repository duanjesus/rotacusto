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
 * Consumo vem das Tabelas PBE Veicular oficiais do INMETRO (gov.br/inmetro) —
 * **anos 2016 a 2026, catálogo completo** (~5.800 modelos/versões no total).
 * Cada ano tem um "ano" próprio no catálogo, então o mesmo modelo pode
 * aparecer várias vezes com o consumo daquele ano específico.
 *
 * {@code tipoEnergia} discrimina COMBUSTAO (usa consumoCidadeKmL/
 * consumoEstradaKmL, em L/km) de ELETRICO (usa consumoKmPorKWh) — os campos
 * do "outro" tipo ficam nulos. Entradas 100% elétricas foram extraídas dos
 * mesmos PDFs 2021-2026 filtrando por Propulsão="Elétrico" (excluindo
 * híbridos plug-in, que ainda têm motor a combustão e ficam de fora por
 * ora). Como a tabela não expõe km/kWh diretamente, o valor vem do "Consumo
 * Energético (MJ/km)" — métrica única, universal a qualquer motorização, que
 * a tabela publica pra todo veículo — convertido via 1 kWh = 3,6 MJ:
 * consumoKmPorKWh = 3,6 / consumoEnergeticoMJporKm. Verificado contra a
 * legenda de estatísticas de 2022 ("Elétricos 60 Modelos"): a extração bateu
 * exatamente 60 entradas elétricas nesse ano.
 *
 * Anos 2021-2025 (fechados numa rodada anterior, mesma técnica pra
 * combustão/híbrido): a extração desses PDFs específicos
 * (via tabula-java) tem colunas com posições inconsistentes até entre páginas
 * do mesmo documento — um mapeamento fixo por ano capturava só uma fração das
 * linhas reais. Solução: detecção por VALOR em vez de posição fixa — marca/
 * modelo/versão/categoria/combustível vêm de cabeçalho re-detectado por
 * página, mas o par consumo cidade/estrada é achado varrendo a linha inteira
 * por números decimais no intervalo plausível de km/l (4-45), pegando sempre
 * os dois últimos válidos (gasolina/diesel, não etanol — mesma convenção dos
 * anos anteriores). Ano 2021 é um caso à parte: só a página 1 do PDF tem
 * cabeçalho de texto (é só legenda, sem dado); as páginas 2-20 (dado real)
 * não repetem o cabeçalho, então usam um mapeamento de posição fixo
 * verificado à mão contra várias linhas reais (HB20, Kicks, 911, Boxster).
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
