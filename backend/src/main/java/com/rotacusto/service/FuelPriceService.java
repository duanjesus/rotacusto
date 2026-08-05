package com.rotacusto.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rotacusto.entity.FuelPrice;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.repository.FuelPriceRepository;
import com.rotacusto.util.AccentUtils;

/**
 * Tabela pequena (~1218 linhas: 81 de fallback por UF + município-específicas,
 * Fase 14) — o cliente carrega ela inteira uma vez e faz o lookup por
 * UF/combustível localmente, em vez de expor um endpoint com filtro por query
 * param.
 */
@Service
public class FuelPriceService {

    private final FuelPriceRepository repository;

    public FuelPriceService(FuelPriceRepository repository) {
        this.repository = repository;
    }

    public List<FuelPrice> findAll() {
        return repository.findAll();
    }

    /**
     * Lookup de 2 níveis usado pela sugestão de posto com melhor preço (Fase
     * 15): tenta o preço específico do MUNICÍPIO primeiro (normalizado via
     * {@link AccentUtils#normalizar}, já que a fonte ANP não tem acento mas o
     * retorno do reverse geocoding tem); sem linha específica, cai pro
     * fallback de UF (linha com {@code municipio == null}) — quando cai nesse
     * fallback, o valor devolvido é a média ESTADUAL, não da cidade
     * pesquisada, mesma limitação de granularidade já documentada desde a
     * Fase 14. {@code Optional.empty()} só quando nem a UF tem dado algum.
     */
    public Optional<Double> buscarPrecoPorMunicipio(String municipio, String uf, TipoCombustivel tipoCombustivel) {
        if (uf == null) {
            return Optional.empty();
        }
        String municipioNormalizado = AccentUtils.normalizar(municipio);
        List<FuelPrice> todos = repository.findAll();

        Optional<Double> especifico = todos.stream()
                .filter(p -> p.getMunicipio() != null
                        && AccentUtils.normalizar(p.getMunicipio()).equals(municipioNormalizado)
                        && p.getUf().equalsIgnoreCase(uf)
                        && p.getTipoCombustivel() == tipoCombustivel)
                .map(FuelPrice::getPrecoMedio)
                .findFirst();
        if (especifico.isPresent()) {
            return especifico;
        }

        return todos.stream()
                .filter(p -> p.getMunicipio() == null && p.getUf().equalsIgnoreCase(uf)
                        && p.getTipoCombustivel() == tipoCombustivel)
                .map(FuelPrice::getPrecoMedio)
                .findFirst();
    }
}
