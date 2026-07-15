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
 * **anos 2016 a 2026, catálogo completo** (7.726 linhas). Cada ano tem um
 * "ano" próprio no catálogo, então o mesmo modelo pode aparecer várias vezes.
 *
 * {@code tipoCombustivel} (GASOLINA/ETANOL/DIESEL/ELETRICO) é parte da
 * IDENTIDADE do registro, não um detalhe: um carro flex vira DUAS linhas
 * (mesmo marca/modelo/ano, uma GASOLINA e uma ETANOL, cada uma com seu
 * consumo real) em vez de uma linha só usando a gasolina como proxy — pedido
 * explícito do usuário pra poder escolher o combustível de verdade na hora
 * de calcular. GASOLINA/ETANOL/DIESEL usam consumoCidadeKmL/EstradaKmL (fica
 * nulo pra ELETRICO); ELETRICO usa consumoKmPorKWh (fica nulo pros outros).
 * GNV **não existe** no catálogo — a tabela do INMETRO só certifica
 * configuração de fábrica (legenda oficial: "Etanol(E)Gasolina(G)Flex(F)
 * Diesel(D)"), e GNV no Brasil é sempre conversão pós-fábrica, fora do
 * escopo do programa de etiquetagem.
 *
 * Extração via tabula-java, com detecção por VALOR em vez de posição fixa
 * (varia demais entre páginas do mesmo PDF e até entre anos): marca/modelo/
 * versão/categoria vêm de cabeçalho re-detectado por página; propulsão e
 * combustível são achados varrendo a linha inteira por palavra/letra
 * conhecida; o(s) par(es) de consumo cidade/estrada são achados varrendo por
 * decimais no intervalo plausível de km/l (4-45) — quando a linha tem 2
 * pares (flex), o primeiro é sempre etanol e o segundo gasolina/diesel,
 * confirmado empiricamente contra várias linhas reais (Onix, Mobi). Anos
 * 2017-2019 não têm coluna de Propulsão na tabela (só passou a existir depois,
 * quando híbridos passaram a ser comuns) — tratados como combustão direto.
 * Anos 2016/2020 têm cabeçalho e dado desalinhados (mesmo problema visto
 * antes em 2021/2022) — mapeamento de posição fixo verificado à mão. 2021 é
 * o caso mais extremo: só a página 1 do PDF tem cabeçalho de texto (é só
 * legenda, sem dado); as páginas de dado real não repetem cabeçalho nenhum.
 *
 * Nomes de modelo com variações decorativas do mesmo carro físico (ex.:
 * "HR-V" vs "HR-V (mod. 26)" vs hífen alternativo) são normalizados antes de
 * gravar — ver {@code normalizeModelo} no script de merge — pra não poluir
 * o catálogo com entradas redundantes que deveriam ser uma só.
 *
 * Desgaste (custoDesgastePorKm) NÃO tem fonte oficial no Brasil — continua
 * sendo uma estimativa por categoria/segmento do próprio INMETRO (Sub
 * Compacto, Compacto, Médio, SUV, Picape etc.), documentada como placeholder
 * a refinar. Representa só desgaste FÍSICO (pneu, óleo, manutenção
 * preventiva proporcional à distância) — **não inclui depreciação do
 * veículo**, faixa aproximada R$0,08/km (micro-compacto) a R$0,24/km
 * (esportivo). Um valor anterior (R$0,28-0,58/km) embutia depreciação
 * implicitamente e o usuário achou alto demais pra uma viagem pontual.
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
