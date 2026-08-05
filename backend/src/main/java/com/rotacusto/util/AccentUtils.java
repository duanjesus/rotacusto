package com.rotacusto.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normaliza texto pra comparação: maiúsculo, sem acento. Necessário porque a
 * fonte ANP de preço de combustível ({@link com.rotacusto.entity.FuelPrice})
 * salva município/UF sem diacrítico (ex. "SAO PAULO"), enquanto o retorno do
 * reverse geocoding do Nominatim ({@link com.rotacusto.client.NominatimClient})
 * vem com acento (ex. "São Paulo") — comparar direto nunca bateria pras
 * cidades acentuadas. Mesma normalização que já existe em Dart
 * (`HomeScreen._chaveMunicipio`, usada no fluxo de preço sugerido do
 * formulário) — essa classe é a versão do lado do servidor, usada pelo fluxo
 * de sugestão de posto com melhor preço (Fase 15).
 */
public final class AccentUtils {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");

    private AccentUtils() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String semAcento = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return semAcento.toUpperCase().trim();
    }
}
