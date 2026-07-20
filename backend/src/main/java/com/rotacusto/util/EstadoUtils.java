package com.rotacusto.util;

import java.util.Map;

/**
 * Nome completo do estado (como o Photon/Nominatim devolvem, ex. "Rio de
 * Janeiro") -> sigla UF. Usado só pra sugerir preço regional de combustível
 * ({@link com.rotacusto.service.FuelPriceService}) a partir do estado da
 * Origem escolhida no autocomplete.
 */
public final class EstadoUtils {

    private static final Map<String, String> UF_POR_NOME = Map.ofEntries(
            Map.entry("Acre", "AC"),
            Map.entry("Alagoas", "AL"),
            Map.entry("Amapá", "AP"),
            Map.entry("Amazonas", "AM"),
            Map.entry("Bahia", "BA"),
            Map.entry("Ceará", "CE"),
            Map.entry("Distrito Federal", "DF"),
            Map.entry("Espírito Santo", "ES"),
            Map.entry("Goiás", "GO"),
            Map.entry("Maranhão", "MA"),
            Map.entry("Mato Grosso", "MT"),
            Map.entry("Mato Grosso do Sul", "MS"),
            Map.entry("Minas Gerais", "MG"),
            Map.entry("Pará", "PA"),
            Map.entry("Paraíba", "PB"),
            Map.entry("Paraná", "PR"),
            Map.entry("Pernambuco", "PE"),
            Map.entry("Piauí", "PI"),
            Map.entry("Rio de Janeiro", "RJ"),
            Map.entry("Rio Grande do Norte", "RN"),
            Map.entry("Rio Grande do Sul", "RS"),
            Map.entry("Rondônia", "RO"),
            Map.entry("Roraima", "RR"),
            Map.entry("Santa Catarina", "SC"),
            Map.entry("São Paulo", "SP"),
            Map.entry("Sergipe", "SE"),
            Map.entry("Tocantins", "TO"));

    private EstadoUtils() {
    }

    /**
     * Retorna a sigla UF pro nome completo do estado, ou {@code null} se não
     * reconhecido (nome vindo de fonte externa, sem garantia de bater exato).
     */
    public static String siglaPorNomeCompleto(String nomeCompleto) {
        if (nomeCompleto == null) {
            return null;
        }
        return UF_POR_NOME.get(nomeCompleto.trim());
    }
}
