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
 * <p><b>Fora do escopo federal</b>: Itaboraí (RJ-116) é rodovia ESTADUAL,
 * não aparece no dataset da ANTT — mantido do fix anterior (fonte:
 * rota116.com.br/tarifas). Rodovias estaduais de outros estados (São Paulo/
 * ARTESP tem a maior malha pedagiada do país, Minas, Paraná, Rio Grande do
 * Sul etc.) ainda não têm fonte aberta estruturada equivalente ao KMZ da
 * ANTT identificada — cada estado tem sua própria agência reguladora, sem
 * um dataset nacional único. Ver punch list pra status de cobertura
 * estadual.
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
