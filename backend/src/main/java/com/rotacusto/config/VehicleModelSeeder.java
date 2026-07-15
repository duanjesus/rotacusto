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
 * Consumo de CARRO vem das Tabelas PBE Veicular oficiais do INMETRO
 * (gov.br/inmetro) — **anos 2016 a 2026, catálogo completo**. Cada ano tem
 * um "ano" próprio no catálogo, então o mesmo modelo pode aparecer várias
 * vezes. Total do catálogo (carro + moto): 7.802 linhas.
 *
 * MOTO (Fase 3, 76 modelos): o PBE do INMETRO **não cobre motocicleta** —
 * não existe tabela oficial de consumo por modelo de moto no Brasil. A
 * cilindrada ({@code cilindradaCC}) é dado público real (ficha técnica do
 * fabricante), curada de múltiplas fontes de mercado (rankings de vendas,
 * especificações de cada marca). O consumo é uma ESTIMATIVA derivada da
 * cilindrada por interpolação linear entre faixas de km/l comumente
 * conhecidas pra motos populares no Brasil (ex.: ~36km/l pra uma 160cc,
 * ~28km/l pra uma 250cc) — não é medição real por modelo. Combustível
 * sempre GASOLINA (simplificação: várias motos populares são flex de
 * verdade, mas separar isso por modelo exigiria pesquisa adicional não
 * feita ainda). Ano fixo em 2025 pra todas — cilindrada não muda
 * ano-a-ano o bastante pra justificar entradas duplicadas por ano.
 *
 * VAN/CAMINHÃO leve/ÔNIBUS micro (Fase 3, parte 1): não são dado novo —
 * são entradas que já existiam no catálogo como CARRO (o PBE do INMETRO
 * certifica alguns veículos comerciais leves junto dos carros de passeio)
 * reclassificadas por nome de modelo conhecido: furgões puros (Ducato,
 * Jumper, Boxer, Fiorino, Sprinter, Transit, Kangoo, Partner/Rapid, Expert,
 * Master Furgão/PRO) viraram VAN; picapes/chassi de carga (Kia Bongo K2500,
 * Master Chassis Cabine) viraram CAMINHAO; o micro-ônibus Master Bus virou
 * ONIBUS. Mantêm o consumo/desgaste REAIS originais — não são estimativa.
 * Ficaram de fora por ambiguidade (nome também usado pra versão de
 * passageiro/família no Brasil): Fiat Doblò (sem sufixo "Cargo") e Citroën
 * Berlingo. Caminhões/ônibus pesados (sem equivalente no PBE) ainda não
 * têm dado no catálogo — pendente de pesquisa por PBT (peso bruto total),
 * o mesmo princípio da cilindrada de moto: spec real e pública guiando uma
 * estimativa de consumo, documentada como tal.
 *
 * CAMINHÃO/ÔNIBUS pesados (Fase 3, parte 2, 28 modelos curados): mesmo
 * princípio da moto, agora com {@code pbtKg} (peso bruto total, ficha
 * técnica pública do fabricante) no lugar da cilindrada. Consumo é
 * ESTIMATIVA por interpolação linear entre faixas de km/l por classe de
 * peso pesquisadas em fontes do setor de transporte (infleet.com.br pra
 * caminhão; ANTP/NBR 15570/SIMEFRE pra ônibus) — não é medição por modelo.
 * Duas faixas de âncora, não uma curva única: caminhão rígido (3,5t→7,0km/l
 * até 29t→2,0km/l) e cavalo mecânico articulado (40t→2,5km/l até
 * 74t→1,8km/l, usando PBTC — peso bruto total combinado — no lugar do PBT
 * do próprio trator, que sozinho não é um número que a indústria costuma
 * publicar). As duas faixas SE SOBREPÕEM de propósito (um rígido perto do
 * limite de 29t roda pior que uma combinação de 40t) — não é bug, reflete
 * que são categorias de veículo diferentes, não pontos na mesma reta.
 * {@code consumoCidadeKmL} = {@code consumoEstradaKmL} × 0,82 (penalidade
 * genérica de cidade, sem fonte específica por modelo). {@code
 * custoDesgastePorKm} escalona por faixa de PBT a partir da mesma faixa
 * "só desgaste físico" usada pra carro (R$0,08-0,24/km), maior pra veículo
 * mais pesado — estimativa minha, sem fonte citável, mesmo espírito do
 * desgaste de carro. Ano fixo em 2025, sempre DIESEL (não existe caminhão/
 * ônibus a gasolina/flex vendido no Brasil). Ônibus ficou com só 5 modelos
 * (não os ~10-12 planejados) porque vários chassis pesquisados (Volvo
 * B270F, Scania K-series) tinham só a ficha técnica em PDF não-legível por
 * busca — preferi um catálogo menor mas com fonte real a inventar PBT.
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
