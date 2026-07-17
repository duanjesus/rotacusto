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
 * As 5 praças do corredor BR-101 RJ→ES (Rio Bonito, Casimiro de Abreu, Campos
 * dos Goytacazes, Marataízes) e a de Itaboraí (RJ-116) tiveram tarifa
 * atualizada pra valor REAL confirmado (não mais estimativa) — usuário
 * reportou imprecisão em pedágio/valores, verificado direto contra as
 * concessionárias:
 * <ul>
 *   <li>Arteris Fluminense (Rio Bonito, Casimiro de Abreu, Campos dos
 *       Goytacazes P1/P2, São Gonçalo): tarifa ÚNICA pras 5 praças desde o
 *       reajuste de 27/06/2025 — R$7,50/carro (tarifaPorEixo=3,75 pra um
 *       carro de 2 eixos), R$3,75/moto. São Gonçalo não está no dataset
 *       (sem coordenada confiável levantada ainda).</li>
 *   <li>Ecovias Capixaba (Marataízes, BR-101/ES) — renomeada de Eco101/
 *       Rodosol; reajuste de 27/02/2026: R$5,20/carro (tarifaPorEixo=2,60),
 *       **moto isenta** (categoria 9 = Exempt na tabela oficial).</li>
 *   <li>Rota 116 (Itaboraí, km 1,9): R$9,60/carro (tarifaPorEixo=4,80),
 *       **moto isenta**. tarifa já estava certa; o que estava ERRADO era o
 *       sentido — o dataset marcava como sentido único ("só na volta"), mas
 *       a própria página de tarifas da Rota 116 declara "Tarifa de pedágio
 *       com cobrança bidirecional" pra essa rodovia. Corrigido removendo
 *       refLat/refLng/cobraApenasIndo (praça sem restrição de sentido conta
 *       nos dois sentidos, ver TollService.passesDirectionConstraint).</li>
 *   <li>Ecovias Ponte/EcoRodovias (Ponte Rio-Niterói, BR-101/RJ km 322+100):
 *       R$6,60/carro, R$3,30/moto (reajuste de 18/03/2026) — tarifa e
 *       sentido já confirmados corretos (cobra só sentido Rio→Niterói/
 *       Itaboraí, confirmado pela própria EcoRodovias).</li>
 * </ul>
 *
 * Coordenada da praça de Rio Bonito também foi corrigida pra usar o ponto
 * real detectado ao vivo via Overpass (a antiga era centro aproximado do
 * município, ~9km longe da praça de verdade — por isso nunca batia dentro
 * do raio de enriquecimento e a praça caía pro fallback "não identificada").
 * As demais praças da Arteris Fluminense continuam com coordenada de centro
 * de município — refinar com fonte oficial de km/coordenada antes de contar
 * com correspondência automática pra elas em toda rota.
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
