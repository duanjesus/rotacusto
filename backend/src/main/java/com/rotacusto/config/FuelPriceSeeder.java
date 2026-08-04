package com.rotacusto.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.entity.FuelPrice;
import com.rotacusto.repository.FuelPriceRepository;

/**
 * Carrega o preço médio de combustível por UF (fallback) e por MUNICÍPIO
 * (quando disponível) a partir de data/fuelprices.json — usado só como valor
 * SUGERIDO no formulário (nunca força o usuário a usar esse número).
 *
 * <h2>Origem dos dados (Fase 14 — granularidade por município)</h2>
 * A mesma página semanal da ANP ("Levantamento de Preços de Combustíveis",
 * dado aberto, Decreto 8.777/2016, {@code gov.br/anp/.../arquivos-lpc/<ano>/})
 * publica dois arquivos por semana: o resumo por estado usado até a Fase 11
 * ({@code resumo_semanal_lpc_*.xlsx}) e um por POSTO REVENDEDOR INDIVIDUAL
 * ({@code revendas_lpc_<inicio>_<fim>.xlsx}) — este último tem uma coluna
 * MUNICÍPIO que o resumo por estado não tem, então a Fase 14 passou a extrair
 * só dele, tornando o {@code resumo_semanal_lpc_*} redundante. Mesma técnica
 * de parse de sempre (zip de XML, atributos de célula extraídos independente
 * de ordem — ver lição documentada abaixo, ainda válida).
 *
 * <p><b>Limitação real que definiu o desenho</b>: o arquivo de revenda não
 * tem latitude/longitude, só endereço em texto — geocodificar as ~19.750
 * linhas via Nominatim (limite de ~1 req/s já respeitado neste projeto) não é
 * viável nem seria uso educado da API pública. Por isso o preço por posto
 * individual plotado no mapa está fora de alcance; o que dá pra extrair de
 * graça é o preço médio por MUNICÍPIO (agregando as linhas por
 * MUNICÍPIO+ESTADO+PRODUTO), bem mais granular que por estado.
 *
 * <p><b>Achado real de ferramenta (ainda relevante)</b>: um regex que assumia
 * o atributo {@code t="s"} (marca célula como referência a string
 * compartilhada) apareceria logo depois de {@code r="..."} falha, porque o
 * XML real tem {@code s="1"} (estilo) NO MEIO (`<c r="A10" s="1" t="s">`).
 * Corrigido capturando os atributos da célula como bloco e extraindo
 * {@code r=}/{@code t=} independente da ordem. Lição: nunca assumir ordem de
 * atributos XML ao parsear com regex.
 *
 * <p><b>Achado novo desta extração</b>: os nomes de MUNICÍPIO e ESTADO no
 * arquivo da ANP vêm sem acento (ex. "SAO PAULO", "GOIANIA") — o campo
 * {@code municipio} salvo aqui reflete isso. Nomes vindos do
 * Photon/Nominatim (usados na busca de endereço) TÊM acento, então qualquer
 * comparação precisa normalizar os dois lados (maiúsculo, sem diacríticos)
 * antes de comparar — feito no cliente Flutter, não aqui.
 *
 * <p>Extraído: preço médio de revenda de GASOLINA COMUM, ETANOL e DIESEL S10
 * (semana de referência 26/07-01/08/2026), convertido pro enum
 * {@link com.rotacusto.entity.enums.TipoCombustivel} do app
 * (GASOLINA/ETANOL/DIESEL). Não inclui GLP/GNV (o app não modela esses
 * combustíveis), GASOLINA ADITIVADA nem DIESEL S500 (preferido o S10, mesmo
 * critério da Fase 11). BANDEIRA (marca do posto) não entra na agregação —
 * fora de escopo desta rodada, o pedido foi granularidade geográfica.
 */
@Component
public class FuelPriceSeeder implements CommandLineRunner {

    private final FuelPriceRepository repository;
    private final ObjectMapper objectMapper;

    public FuelPriceSeeder(FuelPriceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            return;
        }
        try (var input = new ClassPathResource("data/fuelprices.json").getInputStream()) {
            List<FuelPrice> precos = objectMapper.readValue(input, new TypeReference<List<FuelPrice>>() {
            });
            repository.saveAll(precos);
        }
    }
}
