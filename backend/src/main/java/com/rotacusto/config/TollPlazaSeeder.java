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
 * Carrega o dataset-semente nacional de praças de pedágio a partir de
 * data/tollplazas.json.
 *
 * <h2>Origem dos dados (expansão nacional)</h2>
 * Depois do fix pontual do corredor RJ→ES (sentido/tarifa do Itaboraí e da
 * Ponte Rio-Niterói), o usuário pediu a mesma verificação pra **todos os
 * pedágios do Brasil**. Escala real: ANTT cita mais de 400-1.800 pontos de
 * cobrança no país (federais + estaduais) — pesquisar cada um manualmente
 * (o método usado pro RJ) não é viável. Solução: usar fonte de dado
 * ESTRUTURADA em vez de busca ponto-a-ponto.
 *
 * <p><b>Localização/sentido (158 praças, cobertura nacional das rodovias
 * FEDERAIS)</b>: a ANTT publica um dataset aberto oficial —
 * {@code dados.antt.gov.br/dataset/praca-de-pedagio}, formato KMZ — com
 * TODAS as praças de pedágio de TODAS as 23 concessões de rodovia federal em
 * operação: nome da praça, rodovia, km, município, UF, sentido (Crescente/
 * Decrescente/ambos) e coordenada exata. Baixado, extraído (KMZ = zip de
 * KML) e parseado (cada Placemark tem os campos numa tabela HTML dentro do
 * CDATA da descrição). Como cada praça física já vem com SUA PRÓPRIA
 * coordenada exata (não uma aproximação de município), o raio de detecção
 * apertado do OSM (0.1km) já resolve sentido único sozinho pra praças em
 * pistas fisicamente separadas — não precisou do mecanismo de
 * refLat/refLng/cobraApenasIndo pra nenhuma praça nova (esse mecanismo
 * continua reservado pra quando só existe UMA coordenada representando um
 * pedágio que na prática só cobra num sentido, caso da Ponte Rio-Niterói).
 *
 * <p><b>Tarifa (43 das 158 praças federais)</b>: o KMZ da ANTT NÃO tem
 * preço, só localização — tarifa foi pesquisada por concessionária
 * (~23, via WebSearch, uma por vez). Achado real, não óbvio de antemão:
 * tarifa uniforme entre todas as praças da mesma concessão (o padrão que
 * funcionou pro corredor RJ→ES) é a EXCEÇÃO, não a regra — concessões
 * maiores/mais antigas (CCR RioSP/Nova Dutra, EcoRioMinas, Concebra, Via
 * Brasil BR-163, Ecovias do Araguaia) têm tarifa BEM diferente por praça
 * (ex.: EcoRioMinas varia de R$8,90 a R$13,30 entre 6 praças da mesma
 * concessão). Aplicar uma tarifa única nesses casos inventaria erro na
 * mesma escala do bug original — por isso essas concessões ficaram SEM
 * tarifa curada (praça ainda entra no dataset com coordenada real, só cai
 * no fallback de tarifa padrão em vez de ter nome/preço certos). Curadas
 * com confiança (tarifa confirmada uniforme pra toda a concessão, fonte e
 * data documentadas): Autopista Fernão Dias (BR-381, R$3,70/carro, moto
 * isenta desde 12/05/2026), Autopista Litoral Sul (BR-376/101, R$5,70,
 * desde 09/07/2025), Autopista Planalto Sul (BR-116, R$8,70, desde
 * 19/12/2025), Autopista Régis Bittencourt (BR-116, R$4,30, desde
 * 29/12/2025), Autopista Fluminense/Arteris (BR-101/RJ — Rio Bonito,
 * Casimiro de Abreu, Campos dos Goytacazes P1/P2, São Gonçalo — R$7,50/
 * R$3,75 moto, desde 27/06/2025), Eco101/Ecovias Capixaba (BR-101/ES, 7
 * praças, R$5,20, moto isenta), Via Costeira (BR-101/ES, 4 praças, R$5,20,
 * moto isenta, fev/2026), Ecoponte (Ponte Rio-Niterói, R$6,60/R$3,30 moto,
 * 18/03/2026, sentido único confirmado — só cobra indo Rio→Niterói).
 *
 * <p><b>Estadual — São Paulo (151 praças, ARTESP)</b>: cada estado tem sua
 * própria agência reguladora, sem um dataset nacional único como o da ANTT —
 * São Paulo foi o primeiro porque tem a maior malha pedagiada do país. A
 * "Pedágio" dataset da ARTESP em si é só uma página institucional sem
 * arquivo pra baixar; o dado real está no dataset "Malha Rodoviária"
 * (23 KMZ, um por concessão — {@code dadosabertos.artesp.sp.gov.br/dataset/
 * malha-rodoviaria}), majoritariamente geometria de rodovia (dezenas de MB
 * por arquivo), mas cada um tem uma pasta "Praça(s) de Pedágio" com dado bem
 * mais rico que o federal da ANTT: coordenada exata, sentido, tipo de
 * cobrança (uni/bidirecional) **e a tabela de tarifa real (Leves/Comercial
 * por eixo/Motos) já embutida na descrição** — não precisou de pesquisa por
 * concessão feito à mão como no federal, o próprio dataset já tem o preço
 * por praça. Baixado e parseado via regex (arquivo grande demais pra valer
 * a pena um parser XML DOM completo) — mesmo bug do preço total-vs-por-eixo
 * do federal se repetiria aqui se não dividido por 2 (o "Leves" da ARTESP
 * também é preço total de carro, não por eixo). ~30 praças (free-flow
 * eletrônico tipo pórtico, ou desativadas) ficaram sem tarifa curada de
 * propósito — não têm o padrão "Leves = R$X" na descrição porque cobram por
 * km rodado, não por passagem fixa, incompatível com o modelo
 * tarifaPorEixo×eixos deste app. Achado de parsing: o nome da pasta varia
 * de arquivo pra arquivo ("Praças de Pedagio_25" sem acento na Autoban,
 * "Praça de Pedágio" com acento na maioria, "Praças de Pedágio SV" na
 * SPVias) — a regex de busca da pasta precisa aceitar "a" OU "á" depois de
 * "Ped", senão silenciosamente não acha nada em 21 dos 23 arquivos (bug
 * real encontrado rodando pela primeira vez, corrigido antes de qualquer
 * dado entrar no seed).
 *
 * <p><b>Levantamento dos demais estados (concluído, resultado majoritariamente
 * "sem rede estadual relevante" — não é preguiça, é o cenário real)</b>:
 * pedágio ESTADUAL de verdade (fora do que a ANTT já cobre) só existe numa
 * minoria de estados — confirmado via busca antes de tentar processar cada
 * um:
 * <ul>
 *   <li><b>Rio de Janeiro</b>: Itaboraí (RJ-116) coberto (fonte:
 *       rota116.com.br/tarifas). RJ-124 (Via Lagos, CCR) é a única praça
 *       encontrada nesta pesquisa inteira com tarifa genuinamente diferente
 *       por dia da semana — R$18,40/carro em dia útil (12h segunda a 12h
 *       sexta), R$30,60/carro em fim de semana/feriado (12h sexta a 12h
 *       segunda + véspera/dia seguinte de feriado nacional), reajuste de
 *       01/08/2025. Moto isenta. Usuário pediu explicitamente pra usar a
 *       data do dia no cálculo em vez de deixar sem tarifa — {@code
 *       TollPlaza} ganhou {@code tarifaPorEixoFimDeSemana} (nulo em toda
 *       outra praça do dataset) e {@code TollCostCalculator} recebe a data
 *       da viagem como parâmetro explícito (não {@code LocalDate.now()}
 *       interno, mesmo padrão de função pura testável já usado nos
 *       detectores do lado Flutter) pra decidir sábado/domingo → tarifa de
 *       fim de semana. **Feriados nacionais não são detectados** — só
 *       dia da semana; calendário de feriados ficaria desproporcional pra
 *       uma única praça com essa característica, documentado como
 *       simplificação aceita, não escondida. Coordenada aproximada (centro
 *       do trecho "Rodovia Via Lagos" em Boa Esperança via Nominatim, não
 *       o ponto exato da cabine). AGETRANSP (regulador do RJ) só publica
 *       volume de tráfego em dado aberto, não localização/tarifa — a
 *       tarifa veio de busca direta no site da concessionária.</li>
 *   <li><b>São Paulo</b>: ver seção acima, 151 praças.</li>
 *   <li><b>Paraná</b>: tem concessões estaduais históricas, mas o DER-PR
 *       está ativamente **encerrando** pedágios estaduais (confirmado via
 *       notícia "DER/PR reúne diretorias... para o fim dos pedágios") — o
 *       GeoDER (mapa interativo) tem malha viária geral em KML/Shapefile,
 *       mas nenhum dataset específico de praça de pedágio com tarifa foi
 *       encontrado. Baixa prioridade dado que a rede está sendo desativada.</li>
 *   <li><b>Rio Grande do Sul (21 praças novas + 7 enriquecidas)</b>: o
 *       servidor original do dado (DAER-RS, WFS/i3Geo em
 *       {@code i3geo.daer.rs.gov.br}/{@code mapa.daer.rs.gov.br}) continuou
 *       recusando conexão numa sessão posterior — mas o MESMO dataset está
 *       publicado por um serviço diferente, a IEDE/RS (Infraestrutura
 *       Estadual de Dados Espaciais), um FeatureServer ArcGIS público em
 *       {@code iede.rs.gov.br/server/rest/services/DAER/pracas_pedagios/
 *       FeatureServer/0} — respondeu HTTP 200 e devolveu GeoJSON com 33
 *       praças, coordenada exata, rodovia/km, concessão e até um
 *       {@code url_tarifa} por concessão apontando pra página oficial de
 *       tarifas. Lição: quando um serviço de dado geoespacial governamental
 *       está fora do ar, procurar o MESMO dataset publicado num portal
 *       estadual de infraestrutura de dados espaciais (IDE) antes de
 *       desistir — é comum o mesmo dado existir em mais de um serviço.
 *       <p>Das 33 praças do dataset, só 21 são genuinamente ESTADUAIS
 *       (prefixo de rodovia ERS-/RSC-): 10 EGR, 6 CSG, 5 Sacyr. As outras
 *       12 (ECOSUL, 5; CCR ViaSul, 7) rodam em rodovia FEDERAL (prefixo
 *       BRS-/BR-101/116/290/386/392) e já existiam no dataset federal da
 *       ANTT processado nesta mesma sessão — confirmado por nome/coordenada
 *       antes de adicionar, pra não duplicar praça.
 *       <ul>
 *         <li><b>CSG</b> (6 praças, ERS-122/240/446): pedágio "free-flow"
 *             (pórtico eletrônico, sem cabine física) mas com tarifa FIXA
 *             por pórtico — diferente do free-flow paulista, que cobra por
 *             km rodado e por isso ficou sem tarifa curada. Confirmado por
 *             2 fontes independentes e consistentes entre si (artigo do
 *             reajuste de fev/2025 + notícia do reajuste de abr/2026, delta
 *             de R$0,10-0,30 batendo entre as duas). Valores 2026: São
 *             Sebastião do Caí R$13,30, Antônio Prado R$9,20, Ipê R$9,30,
 *             Capela de Santana R$9,70, Farroupilha R$11,50, Carlos Barbosa
 *             R$10,60 (preço total carro 2 eixos, dividido por 2 na
 *             curadoria, mesma convenção de sempre).</li>
 *         <li><b>Sacyr/Rota de Santa Maria</b> (5 praças, RSC-287): tarifa
 *             ÚNICA confirmada pro trecho inteiro — R$5,40/carro (2026),
 *             R$2,70/eixo, moto R$2,70 (tarifa direta, não dividida).</li>
 *         <li><b>EGR</b> (10 praças, ERS-235/115/239/474/130/040/135/453):
 *             tarifa varia MUITO por praça (ex.: Gramado R$7,10 vs Coxilha
 *             R$4,40 em dado de 2024) e a tabela oficial só existe em
 *             IMAGEM no site da EGR (não extraível por texto), sem fonte em
 *             texto pra confirmar valor 2026 por praça — deixado sem
 *             tarifa curada de propósito, mesmo critério da EcoRioMinas.</li>
 *         <li><b>CCR ViaSul (agora "Motiva")</b>: JÁ EXISTIA no dataset
 *             federal (concessão "Via Sul", 7 praças BR-101/290/386, sem
 *             tarifa) — enriquecida agora com o valor uniforme confirmado
 *             por múltiplas notícias locais consistentes e datadas:
 *             R$6,60/carro = R$3,30/eixo, vigente desde 26/06/2026.</li>
 *         <li><b>Ecosul</b>: JÁ EXISTIA no dataset federal (5 praças
 *             BR-116/392), mantida SEM tarifa curada de propósito — as
 *             fontes encontradas eram genuinamente conflitantes (R$12,30
 *             num portal da ANTT vs R$19,60→R$22,20 em notícias datadas) e
 *             a própria ANTT confirmou que o reajuste pra R$22,20 foi
 *             aprovado mas está "sem impacto imediato para os usuários",
 *             com o contrato de concessão previsto pra terminar em
 *             março/2026 (o dataset é de julho/2026 — a concessão pode já
 *             ter encerrado). Preferir deixar sem tarifa a arriscar um
 *             valor errado ou de uma concessão extinta.</li>
 *       </ul></li>
 *   <li><b>Minas Gerais, Bahia, Santa Catarina, Goiás e demais</b>: sem
 *       rede de pedágio ESTADUAL relevante identificada — os pedágios
 *       nesses estados são predominantemente em rodovias FEDERAIS (BR-xxx),
 *       já cobertas pelo dataset da ANTT. Santa Catarina, por exemplo, não
 *       tem nenhuma praça estadual em operação ainda (primeira prevista só
 *       pra 2026, rodovia Via Mar).</li>
 * </ul>
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
        try (var input = new ClassPathResource("data/tollplazas.json").getInputStream()) {
            List<TollPlaza> plazas = objectMapper.readValue(input, new TypeReference<List<TollPlaza>>() {
            });
            repository.saveAll(plazas);
        }
    }
}
