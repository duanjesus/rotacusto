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
 * Carrega o preço médio de combustível por UF a partir de
 * data/fuelprices.json — usado só como valor SUGERIDO no formulário (nunca
 * força o usuário a usar esse número).
 *
 * <h2>Origem dos dados</h2>
 * ANP publica semanalmente o "Levantamento de Preços de Combustíveis" (dado
 * aberto, Decreto 8.777/2016) — planilha {@code resumo_semanal_lpc_<inicio>_
 * <fim>.xlsx} em {@code gov.br/anp/.../arquivos-lpc/<ano>/}, aba "ESTADOS"
 * com preço médio de revenda por UF/produto. Baixada e extraída via parse
 * direto do XML interno do .xlsx (zip de XML — mesma natureza de um KMZ,
 * já visto antes neste projeto).
 *
 * <p><b>Achado real de ferramenta</b>: a primeira tentativa de parse achou só
 * códigos numéricos nas células (ex. produto "37", estado "272") sem
 * legenda em lugar nenhum do arquivo — a causa não era falta de dado, era um
 * bug no parser: um regex que assumia o atributo {@code t="s"} (marca célula
 * como referência a string compartilhada) apareceria logo depois de
 * {@code r="..."}, mas o XML real tem {@code s="1"} (estilo) NO MEIO
 * (`<c r="A10" s="1" t="s">`), fazendo o regex simplificado nunca casar o
 * {@code t="s"} e tratar o índice da string como se fosse um número cru.
 * Corrigido capturando os atributos da célula como bloco e extraindo
 * {@code r=}/{@code t=} independente da ordem — depois disso os rótulos
 * (ESTADO, PRODUTO, "GASOLINA COMUM" etc.) apareceram perfeitamente legíveis.
 * Lição: nunca assumir ordem de atributos XML ao parsear com regex.
 *
 * <p>Extraído: preço médio de revenda de GASOLINA COMUM, ETANOL HIDRATADO e
 * ÓLEO DIESEL S10 pras 27 UFs (semana de referência 12-18/07/2026), convertido
 * pro enum {@link com.rotacusto.entity.enums.TipoCombustivel} do app
 * (GASOLINA/ETANOL/DIESEL). Não inclui GLP/GNV (o app não modela esses
 * combustíveis) nem ÓLEO DIESEL comum/S500 (preferido o S10, mais moderno e
 * com cobertura completa nas 27 UFs — o "comum" tinha uma lacuna no Amapá).
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
